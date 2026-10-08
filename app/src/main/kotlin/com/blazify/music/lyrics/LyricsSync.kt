/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.lyrics

import java.util.Locale

/**
 * The arithmetic behind syncing a song's lyrics by hand: which lines get a time, and how the
 * times tapped are written down. Kept apart from the screen so it can be checked without one.
 */
object LyricsSync {
    /** What a tag line like [ar:Artist] or [length:03:12] looks like: not a line anyone sings. */
    private val TAG_LINE = Regex("^\\[[a-zA-Z]+:[^\\]]*]$")

    /** A word's own time inside a line, as word-by-word lyrics carry them: <00:12.34>. */
    private val WORD_TIME = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")

    /**
     * The lines of [lyrics] to tap a time for. Blank lines are passed over, tags are left out,
     * and lyrics that already have times give their words back without them, so a song can be
     * synced again.
     */
    fun linesToSync(lyrics: String): List<String> =
        lyrics.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || TAG_LINE.matches(line)) return@mapNotNull null
            val words = LINE_REGEX.matchEntire(line)?.groupValues?.get(3) ?: line
            words.replace(WORD_TIME, " ").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
        }

    /**
     * [lines] with the [times] tapped for them, in milliseconds, as timed lyrics. The digits are
     * written in the one way the lyrics reader understands, whatever language the phone is in.
     */
    fun toTimedLyrics(lines: List<String>, times: List<Long>): String =
        lines.zip(times).joinToString("\n") { (line, ms) ->
            String.format(Locale.ROOT, "[%02d:%02d.%02d]%s", ms / 60_000, ms / 1000 % 60, ms % 1000 / 10, line)
        }

    /** Who the lyrics are marked as coming from once someone has synced them themselves. */
    const val PROVIDER = "Synced by you"
}
