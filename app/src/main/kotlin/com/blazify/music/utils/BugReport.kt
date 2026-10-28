/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.blazify.music.BuildConfig
import java.net.URLEncoder
import java.util.Locale

/**
 * Reporting a problem without leaving the app.
 *
 * There is nothing clever here, and that is the point. Almost every report
 * that never gets sent is lost at the step where somebody has to find out
 * where to send it, then work out which version they are on, then describe
 * their phone. This fills all of that in and leaves them the one part only
 * they can write.
 *
 * The details are shown before anything is sent. A report that quietly
 * collects facts about someone's device and posts them is the sort of thing
 * this app exists to avoid, even when the facts are harmless.
 */
object BugReport {
    private const val ISSUES = "https://github.com/rajendra7169/blazify/issues/new"

    /** Everything that would otherwise be the first three questions of a reply. */
    fun details(): String =
        buildString {
            appendLine("Arctic Fresh ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            append("Language ${Locale.getDefault()}")
        }

    /** The bug report form, with the version and phone already filled in. */
    fun issueUrl(): String =
        issueUrl(
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT}), language ${Locale.getDefault()}",
        )

    /**
     * The form fills each box from a query parameter named after the field's
     * id, so these land in their own boxes instead of one block of text in an
     * otherwise blank issue. The ids are the ones in
     * .github/ISSUE_TEMPLATE/bug_report.yml, and renaming one there leaves its
     * box empty here without any error.
     */
    internal fun issueUrl(version: String, device: String): String =
        "$ISSUES?template=bug_report.yml&version=${encode(version)}&device=${encode(device)}"

    /** mailto: wants percent-encoding, and a literal + is a space to a mail client. */
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** For anyone without a GitHub account, or without a connection right now. */
    fun copyDetails(context: Context) {
        context.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText("Arctic Fresh", plainBody()))
    }

    /** The same questions without the markdown, which an email client will not render. */
    private fun plainBody(): String =
        """
        What happened:


        What you expected instead:


        How to make it happen again:
        1.
        2.

        ---
        ${details()}
        """.trimIndent()
}
