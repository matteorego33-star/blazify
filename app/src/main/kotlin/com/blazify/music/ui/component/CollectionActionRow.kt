/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.blazify.music.R

private val SideButton = 46.dp
private val PlayButton = 68.dp

/**
 * The buttons under a playlist's or an album's cover: keep it and download it
 * on the left, play in the middle, shuffle and more on the right.
 *
 * Always five places, so Play sits in the middle of the screen whatever the
 * page offers — a page with no shuffle keeps an empty place for it rather
 * than sliding Play off to one side.
 *
 * [downloadState] is a media3 [Download] state for the whole list, or null
 * where downloading does not apply (a podcast, an empty list).
 */
@Composable
fun CollectionActionRow(
    liked: Boolean,
    onLike: () -> Unit,
    downloadState: Int?,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: (() -> Unit)?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            description = stringResource(R.string.like),
            tint = if (liked) MaterialTheme.colorScheme.error else null,
            onClick = onLike,
        )

        if (downloadState != null) {
            val done = downloadState == Download.STATE_COMPLETED
            val busy = downloadState == Download.STATE_QUEUED || downloadState == Download.STATE_DOWNLOADING
            RoundButton(
                icon = if (done) R.drawable.offline else R.drawable.download,
                description = stringResource(if (done) R.string.offline else R.string.action_download),
                tint = if (done) MaterialTheme.colorScheme.primary else null,
                enabled = !done && !busy,
                busy = busy,
                onClick = onDownload,
            )
        } else {
            Spacer(Modifier.size(SideButton))
        }

        Surface(
            onClick = onPlay,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier.size(PlayButton),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (onShuffle != null) {
            RoundButton(
                icon = R.drawable.shuffle,
                description = stringResource(R.string.shuffle),
                onClick = onShuffle,
            )
        } else {
            Spacer(Modifier.size(SideButton))
        }

        RoundButton(
            icon = R.drawable.more_vert,
            description = stringResource(R.string.more_options),
            onClick = onMore,
        )
    }
}

@Composable
private fun RoundButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tint: Color? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    size: Dp = SideButton,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (busy) {
                BlazeLoader(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description,
                    tint = tint ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
