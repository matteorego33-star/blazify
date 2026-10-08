/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.menu

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blazify.music.LocalDatabase
import com.blazify.music.LocalPlayerConnection
import com.blazify.music.R
import com.blazify.music.db.entities.LyricsEntity
import com.blazify.music.lyrics.LyricsSync
import java.util.Locale

/**
 * Times a song's plain lyrics by hand: the song plays, and a tap as each line begins gives that
 * line its time. Saved, the lyrics scroll and light up with the song like any synced lyrics.
 * Nothing is written until Save, so leaving part way changes nothing.
 */
@Composable
fun SyncLyricsDialog(
    songId: String,
    songTitle: String,
    lyrics: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playing by playerConnection.mediaMetadata.collectAsState()
    val isThisSong = playing?.id == songId

    val lines = remember(lyrics) { LyricsSync.linesToSync(lyrics) }
    val times = remember { mutableStateListOf<Long>() }
    val next = times.size
    val done = next >= lines.size
    var confirmLeave by remember { mutableStateOf(false) }

    val leave = { if (times.isEmpty()) onDismiss() else confirmLeave = true }

    // The song starts again from the top, playing: the first line is about to begin.
    LaunchedEffect(Unit) {
        if (isThisSong) {
            playerConnection.player.seekTo(0)
            playerConnection.player.play()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(next) {
        if (lines.isNotEmpty()) listState.animateScrollToItem((next - 2).coerceIn(0, lines.lastIndex))
    }

    Dialog(
        onDismissRequest = leave,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                // Edge to edge, so the menu it was opened from does not show under the navigation bar.
                decorFitsSystemWindows = false,
            ),
    ) {
        BackHandler(onBack = leave)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    IconButton(onClick = leave) {
                        Icon(painterResource(R.drawable.close), contentDescription = stringResource(android.R.string.cancel))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            stringResource(R.string.sync_lyrics_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            songTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        enabled = done,
                        onClick = {
                            val timed = LyricsSync.toTimedLyrics(lines, times)
                            database.query {
                                upsert(LyricsEntity(id = songId, lyrics = timed, provider = LyricsSync.PROVIDER))
                            }
                            Toast.makeText(context, R.string.sync_lyrics_saved, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.save)) }
                }

                Text(
                    text =
                        when {
                            !isThisSong -> stringResource(R.string.sync_lyrics_other_song)
                            done -> stringResource(R.string.sync_lyrics_done)
                            else -> stringResource(R.string.sync_lyrics_hint) + " · " +
                                stringResource(R.string.sync_lyrics_progress, next, lines.size)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isThisSong) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isNext = index == next
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = times.getOrNull(index)?.let(::clock).orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(52.dp),
                            )
                            Text(
                                text = line,
                                fontSize = if (isNext) 22.sp else 18.sp,
                                lineHeight = if (isNext) 28.sp else 24.sp,
                                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    when {
                                        isNext -> MaterialTheme.colorScheme.primary
                                        index < next -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    OutlinedButton(
                        enabled = times.isNotEmpty(),
                        onClick = {
                            // A step back, and a few seconds of the song again to tap it better.
                            val last = times.removeAt(times.lastIndex)
                            playerConnection.player.seekTo((last - UNDO_REWIND_MS).coerceAtLeast(0))
                        },
                    ) { Text(stringResource(R.string.undo)) }

                    Button(
                        enabled = isThisSong && !done,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(96.dp),
                        onClick = {
                            val now = playerConnection.player.currentPosition
                            // A line cannot begin before the one before it.
                            times += maxOf(now, (times.lastOrNull() ?: -1) + 1)
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.sync_lyrics_tap), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        enabled = times.isNotEmpty(),
                        onClick = {
                            times.clear()
                            playerConnection.player.seekTo(0)
                            playerConnection.player.play()
                        },
                    ) { Text(stringResource(R.string.sync_lyrics_restart)) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.sync_lyrics_leave)) },
            text = { Text(stringResource(R.string.sync_lyrics_leave_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onDismiss()
                }) { Text(stringResource(R.string.sync_lyrics_leave_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** A time as the lyrics list shows it beside a line: 1:05.3. */
private fun clock(ms: Long): String =
    String.format(Locale.ROOT, "%d:%02d.%d", ms / 60_000, ms / 1000 % 60, ms % 1000 / 100)

/** How far Undo takes the song back before the line it takes the time off. */
private const val UNDO_REWIND_MS = 3_000L
