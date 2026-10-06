/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Fetching a new version and handing it to the system installer.
 *
 * Two things here are easy to get wrong and expensive to get wrong.
 *
 * The first is where the file goes. The obvious place is the app's own storage,
 * and it is the one place it must not go: when a build is signed with a
 * different key the system refuses to install it over the old one, so the old
 * one has to be removed first — and removing it deletes the app's storage,
 * taking the downloaded file with it. Somebody follows the instruction and finds
 * they have uninstalled their music player with nothing to put back. So it is
 * written to the shared Downloads folder, which outlives the app that put it
 * there.
 *
 * The second is knowing which situation you are in. A same-key build installs
 * straight over the top and keeps everything; a different-key build cannot.
 * Telling somebody to uninstall when they did not have to would cost them their
 * library for nothing, so the certificate inside the downloaded file is compared
 * with the one already installed and the instruction given is the true one.
 */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"
    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * The same HTTP stack the player already streams through.
     *
     * Not an arbitrary preference: measured on the phone this was built for, the
     * other client could not complete this download at all — it reported a
     * connect timeout against the host GitHub redirects release downloads to,
     * with sixty seconds allowed and the name resolving perfectly well. This one
     * fetches audio from a CDN on the same device all day.
     *
     * Timeouts are per-socket rather than overall, because the only sane limit
     * on tens of megabytes over an unknown connection is the connection going
     * quiet, not how long the whole thing takes.
     */
    private val http by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Where a fetched build ended up, and what has to happen to it now. */
    data class Fetched(
        val uri: Uri,
        val displayName: String,
        /**
         * Whether the installed version has to be removed by hand first,
         * because this build carries a different signing certificate. Android
         * treats that as a different application whatever the name says, and
         * nothing in this app can work around it.
         */
        val needsUninstallFirst: Boolean,
    )

    /** Tries on one address before moving to the next, for a dropped connection. */
    private const val TRIES_PER_HOST = 3

    /**
     * Download a release asset into the shared Downloads folder.
     *
     * [urls] are the same file in different places, tried in order: a host
     * that refuses or keeps dropping hands over to the next, and the next
     * carries on from the bytes already saved rather than starting again.
     * [onProgress] receives bytes so far and the total, which is null when the
     * server does not declare one.
     */
    suspend fun fetch(
        context: Context,
        urls: List<String>,
        displayName: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): Result<Fetched> = withContext(Dispatchers.IO) {
        runCatching {
            require(urls.isNotEmpty()) { "No download address" }
            val sink = openDownloadsSink(context, displayName)
            var savedAs = displayName
            try {
                sink.stream.use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var total: Long? = null
                    var host = 0
                    var tries = 0
                    var lastError: Throwable? = null
                    while (true) {
                        if (tries >= TRIES_PER_HOST) {
                            host++
                            tries = 0
                        }
                        val url = urls.getOrNull(host) ?: throw (lastError ?: error("Every download address failed"))
                        tries++
                        val request = okhttp3.Request.Builder().url(url).apply {
                            if (done > 0) header("Range", "bytes=$done-")
                        }.build()
                        val finished = try {
                            http.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) {
                                    // A refusal will not change on asking again.
                                    tries = TRIES_PER_HOST
                                    throw java.io.IOException("HTTP ${response.code}")
                                }
                                val body = response.body ?: throw java.io.IOException("The server sent nothing")
                                val resumed = response.code == 206
                                val length = body.contentLength().takeIf { it > 0 }
                                    ?.let { if (resumed) it + done else it }
                                if (total == null) {
                                    total = length
                                } else if (length != null && length != total) {
                                    // Not the same file as the bytes already saved.
                                    tries = TRIES_PER_HOST
                                    throw java.io.IOException("$url has a different file ($length bytes, expected $total)")
                                }
                                body.byteStream().use { input ->
                                    // A server that ignores the range sends it all again;
                                    // pass over what is already written.
                                    if (!resumed && done > 0) {
                                        var skip = done
                                        while (skip > 0) {
                                            val n = input.skip(skip)
                                            if (n <= 0) throw java.io.IOException("The download restarted and could not catch up")
                                            skip -= n
                                        }
                                    }
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        out.write(buffer, 0, read)
                                        done += read
                                        onProgress(done, total)
                                    }
                                }
                                total == null || done >= total!!
                            }
                        } catch (e: java.io.IOException) {
                            lastError = e
                            Timber.tag(TAG).w("Download from ${url.substringAfter("://").substringBefore('/')} stopped at $done bytes: ${e.message}")
                            false
                        }
                        if (finished) {
                            Timber.tag(TAG).i("Downloaded $displayName from ${url.substringAfter("://").substringBefore('/')}")
                            break
                        }
                        kotlinx.coroutines.delay(1_500L * tries)
                    }
                    out.flush()
                }
                savedAs = sink.publish()
            } catch (t: Throwable) {
                sink.abandon()
                throw t
            }

            val needsUninstall = !signedLikeInstalled(context, sink.uri)
            Timber.tag(TAG).d("Fetched $savedAs; uninstall first: $needsUninstall")
            Fetched(sink.uri, savedAs, needsUninstall)
        }.onFailure { Timber.tag(TAG).w(it, "Could not fetch the update") }
    }

    /**
     * Whether the system will let this build install over the one already here.
     *
     * A build whose certificate cannot be read counts as "it will not", which is
     * the safe way round: the worst outcome is an instruction to uninstall that
     * was not strictly needed, rather than an install that fails on a screen
     * offering nothing to do about it.
     */
    private fun signedLikeInstalled(context: Context, apk: Uri): Boolean = runCatching {
        val local = copyForInspection(context, apk) ?: return false
        try {
            val installed = certificatesOf(context, path = null)
            val incoming = certificatesOf(context, path = local.absolutePath)
            installed.isNotEmpty() && incoming.isNotEmpty() &&
                installed.intersect(incoming).isNotEmpty()
        } finally {
            local.delete()
        }
    }.getOrDefault(false)

    /** SHA-256 of every signing certificate, for the installed app or for a file. */
    @SuppressLint("PackageManagerGetSignatures")
    private fun certificatesOf(context: Context, path: String?): Set<String> {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val info = if (path == null) {
            pm.getPackageInfo(context.packageName, flags)
        } else {
            pm.getPackageArchiveInfo(path, flags)
        } ?: return emptySet()

        val signers = info.signingInfo?.let {
            if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
        } ?: return emptySet()

        val digest = MessageDigest.getInstance("SHA-256")
        return signers.filterNotNull()
            .map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            .toSet()
    }

    /**
     * The archive parser wants a real path and a MediaStore entry is not one, so
     * a copy is made purely to be read and deleted again.
     */
    private fun copyForInspection(context: Context, uri: Uri): File? = runCatching {
        val out = File(context.cacheDir, "update-inspect.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        out
    }.getOrNull()

    /** Open the system installer on a fetched build. */
    fun install(context: Context, fetched: Fetched) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fetched.uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.tag(TAG).w(it, "No installer would open the file") }
    }

    /**
     * Whether this app may ask for an install at all. Android grants that
     * per-app, and without it the installer opens on a screen that refuses
     * rather than on the install button.
     */
    fun mayInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Send somebody to the screen where that permission is granted. */
    fun requestInstallPermission(context: Context) {
        val direct = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // --- writing into shared Downloads -------------------------------------

    private class Sink(
        val uri: Uri,
        val stream: OutputStream,
        /** Makes the file visible; returns the name it ended up under. */
        val publish: () -> String,
        val abandon: () -> Unit,
    )

    private fun openDownloadsSink(context: Context, displayName: String): Sink =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ) ?: error("The Downloads folder would not accept the file")

            Sink(
                uri = uri,
                stream = resolver.openOutputStream(uri) ?: error("Could not write to Downloads"),
                publish = {
                    // The name can be held by an entry whose file is gone (deleted by
                    // something that did not tell the media store), and Android then
                    // refuses it. Take the next free "Blazify (1).apk" rather than
                    // throw away a finished download over its name.
                    val base = displayName.substringBeforeLast('.')
                    val ext = displayName.substringAfterLast('.', "")
                    val names = sequenceOf(displayName) +
                        (1..20).asSequence().map { "$base ($it)" + if (ext.isEmpty()) "" else ".$ext" }
                    names.firstOrNull { name ->
                        runCatching {
                            resolver.update(
                                uri,
                                ContentValues().apply {
                                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                                    put(MediaStore.Downloads.IS_PENDING, 0)
                                },
                                null,
                                null,
                            )
                        }.onFailure { Timber.tag(TAG).w("Downloads would not take the name $name: ${it.message}") }
                            .isSuccess
                    } ?: error("The Downloads folder would not take the file under any name")
                    // Android may still pick its own free name; report the one it kept.
                    runCatching {
                        resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull() ?: displayName
                },
                abandon = { runCatching { resolver.delete(uri, null, null) } },
            )
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, displayName)
            Sink(
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    file,
                ),
                stream = file.outputStream(),
                publish = { displayName },
                abandon = { file.delete() },
            )
        }
}
