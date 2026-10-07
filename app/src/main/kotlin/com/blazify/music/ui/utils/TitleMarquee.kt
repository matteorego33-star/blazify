/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.utils

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How a song's title and artist scroll when they are too long to fit: twice, a moment
 * after the song starts, then at rest back at the beginning where all of it can be read.
 *
 * They used to scroll for ever, and a title that never stops moving redraws the screen
 * sixty times a second for as long as the app is open — on every page, since the mini
 * player is on every page — which is battery spent on nothing. Before that they scrolled
 * once and could be cut off mid-way, half a word showing; a finished pass ends where it
 * began, so stopping after whole passes leaves the start of the title in view.
 */
fun Modifier.titleMarquee(): Modifier =
    basicMarquee(
        iterations = 2,
        initialDelayMillis = 2500,
        repeatDelayMillis = 2000,
        velocity = 30.dp,
    )

/**
 * Starts the scrolling afresh for [text]: a new song's title gets its two passes even
 * when it happens to be the same width as the last one.
 */
@Composable
fun ScrollsFor(text: Any?, content: @Composable () -> Unit) = key(text) { content() }
