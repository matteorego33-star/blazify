/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Opens a long list on the song playing from it, as the iPhone does: once, as the list
 * arrives, with that song near the middle of the screen. After that the list is the
 * listener's to scroll. It replaces a "Jump to playing" pill that sat over the songs and
 * had to be found and pressed every time.
 *
 * [index] is the song's place in the list, counting whatever sits above the songs, or
 * null while it is not in the list (or the list is still loading).
 */
@Composable
fun OpenOnPlaying(
    lazyListState: LazyListState,
    index: Int?,
) {
    var done by rememberSaveable { mutableStateOf(false) }
    val target by rememberUpdatedState(index)
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        val at = snapshotFlow { target }.filter { it != null }.first() ?: return@LaunchedEffect
        // Wait for the list to be laid out, so its height is known.
        snapshotFlow { lazyListState.layoutInfo.viewportSize.height }.filter { it > 0 }.first()
        done = true
        if (lazyListState.layoutInfo.visibleItemsInfo.any { it.index == at }) return@LaunchedEffect
        lazyListState.scrollToItem(at, scrollOffset = -lazyListState.layoutInfo.viewportSize.height / 3)
    }
}
