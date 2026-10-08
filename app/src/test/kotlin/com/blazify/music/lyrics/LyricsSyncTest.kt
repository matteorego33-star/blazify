/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LyricsSyncTest {
    @Test
    fun `blank lines and tags are passed over`() {
        val lines = LyricsSync.linesToSync("[ar:Someone]\n\nFirst line\n  \nSecond line\n")
        assertEquals(listOf("First line", "Second line"), lines)
    }

    @Test
    fun `lyrics that already have times give back their words`() {
        val lines = LyricsSync.linesToSync("[00:01.20]Hello\n[00:04.00] World")
        assertEquals(listOf("Hello", "World"), lines)
    }

    @Test
    fun `a section heading people sing around stays a line`() {
        assertEquals(listOf("[Verse 1]", "Words"), LyricsSync.linesToSync("[Verse 1]\nWords"))
    }

    @Test
    fun `times are written so the lyrics reader reads them back`() {
        val timed = LyricsSync.toTimedLyrics(listOf("Hello", "World"), listOf(1_230L, 65_400L))
        assertEquals("[00:01.23]Hello\n[01:05.40]World", timed)
        val read = LyricsUtils.parseLyrics(timed).filter { it.text.isNotBlank() }
        assertEquals(listOf(1_230L, 65_400L), read.map { it.time })
    }

    @Test
    fun `times are written in western digits whatever the phone's language`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val timed = LyricsSync.toTimedLyrics(listOf("Line"), listOf(83_450L))
            assertEquals("[01:23.45]Line", timed)
            assertTrue(LINE_REGEX.matches(timed))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `word-by-word times come out, leaving the words`() {
        val lines = LyricsSync.linesToSync("[00:11.01]<00:11.01> You <00:11.29> were <00:11.37> the")
        assertEquals(listOf("You were the"), lines)
    }
}
