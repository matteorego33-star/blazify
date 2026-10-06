/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The updater's two sources: GitHub first, then whatever the website's file
 * points to. Each test stands in for both with a fake network and says what
 * each one answers.
 */
// Robolectric, for Android's own org.json — the plain JVM has only stubs of it.
@RunWith(RobolectricTestRunner::class)
class UpdaterSourcesTest {
    private val github = "https://github.test/repos/blazify"
    private val home = "https://site.test/blazify/home.json"
    private val json = headersOf("Content-Type", "application/json")

    private fun release(tag: String, apk: String, url: String) = """
        {"tag_name":"$tag","body":"notes for $tag","published_at":"2026-10-06T00:00:00Z",
         "prerelease":false,
         "assets":[{"name":"$apk","browser_download_url":"$url","size":27000000}]}
    """.trimIndent()

    /** A fake network: each address answers what [routes] says, anything else is a 404. */
    private fun network(routes: Map<String, Pair<HttpStatusCode, String>>) {
        Updater.client = HttpClient(MockEngine { request ->
            val (status, body) = routes[request.url.toString()] ?: (HttpStatusCode.NotFound to "{}")
            respond(body, status, json)
        })
    }

    @Before
    fun setUp() {
        Updater.primaryApi = github
        Updater.backupHome = home
    }

    @After
    fun tearDown() {
        Updater.client = HttpClient()
        Updater.primaryApi = "https://api.github.com/repos/rajendra7169/blazify"
        Updater.backupHome = "https://rajendrapandey.info.np/blazify/home.json"
    }

    @Test
    fun `GitHub answering is used, and the website is not needed`() = runBlocking {
        network(mapOf(
            "$github/releases/latest" to (HttpStatusCode.OK to release("v9.17.0", "Blazify.apk", "https://github.test/9.17.0/Blazify.apk")),
        ))
        val r = Updater.latestFromAnySource(includeBetas = false)
        assertEquals("9.17.0", r.versionName)
        assertEquals("GitHub", Updater.lastSource)
        assertEquals("https://github.test/9.17.0/Blazify.apk", r.assets.single().downloadUrl)
    }

    @Test
    fun `GitHub refusing sends the updater to the release site the website names`() = runBlocking {
        network(mapOf(
            "$github/releases/latest" to (HttpStatusCode.Forbidden to """{"message":"Repository access blocked"}"""),
            home to (HttpStatusCode.OK to """{"releases":"https://codeberg.test/api/v1/repos/blazify"}"""),
            "https://codeberg.test/api/v1/repos/blazify/releases/latest" to
                (HttpStatusCode.OK to release("v9.17.0", "Blazify.apk", "https://codeberg.test/9.17.0/Blazify.apk")),
        ))
        val r = Updater.latestFromAnySource(includeBetas = false)
        assertEquals("9.17.0", r.versionName)
        assertEquals("codeberg.test", Updater.lastSource)
        assertEquals("https://codeberg.test/9.17.0/Blazify.apk", r.assets.single().downloadUrl)
    }

    @Test
    fun `GitHub down and the website listing the files directly works from any host`() = runBlocking {
        network(mapOf(
            home to (HttpStatusCode.OK to """
                {"latest":{"version":"9.17.0","notes":"Moved for now","date":"2026-10-06",
                 "apks":[{"name":"Blazify.apk","url":"https://files.test/Blazify-9.17.0.apk"},
                         {"name":"Blazify-gms.apk","url":"https://files.test/Blazify-gms-9.17.0.apk"},
                         {"name":"Blazify-izzy-9.17.0.apk","url":"https://files.test/izzy.apk"}]}}
            """.trimIndent()),
        ))
        val r = Updater.latestFromAnySource(includeBetas = false)
        assertEquals("9.17.0", r.versionName)
        assertEquals("rajendrapandey.info.np", Updater.lastSource)
        // The F-Droid build is never offered by the in-app updater, from any source.
        assertEquals(2, r.assets.size)
        assertTrue(r.assets.any { it.variant == "gms" && it.downloadUrl.endsWith("gms-9.17.0.apk") })
        assertTrue(r.assets.any { it.variant == "foss" && it.downloadUrl.endsWith("Blazify-9.17.0.apk") })
    }

    @Test
    fun `an APK listed on several hosts keeps them in order, best first`() = runBlocking {
        network(mapOf(
            home to (HttpStatusCode.OK to """
                {"latest":{"version":"9.17.0","apks":[{"name":"Blazify.apk","size":27000000,
                 "urls":["https://gitlab.test/Blazify.apk","https://codeberg.test/Blazify.apk"]}]}}
            """.trimIndent()),
        ))
        val asset = Updater.latestFromAnySource(includeBetas = false).assets.single()
        assertEquals(listOf("https://gitlab.test/Blazify.apk", "https://codeberg.test/Blazify.apk"), asset.allUrls)
    }

    @Test
    fun `an unencrypted download address in the website's file is refused`() {
        val failed = runCatching {
            Updater.parseManifest(org.json.JSONObject(
                """{"version":"9.17.0","apks":[{"name":"Blazify.apk","url":"http://files.test/Blazify.apk"}]}""",
            ))
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `both sources failing says so, naming each`() = runBlocking {
        network(emptyMap())
        val failed = runCatching { Updater.latestFromAnySource(includeBetas = false) }
        val message = failed.exceptionOrNull()?.message.orEmpty()
        assertTrue(failed.isFailure)
        assertTrue(message.contains("GitHub") && message.contains("backup"))
    }
}
