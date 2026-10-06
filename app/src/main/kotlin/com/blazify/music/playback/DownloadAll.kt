/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.blazify.music.db.MusicDatabase
import com.blazify.music.models.MediaMetadata

/**
 * Keeps a whole list of songs on the phone: a playlist, a shelf, an album
 * opened from Browse.
 *
 * Each song is saved first, so it shows in Downloads with its own name and
 * cover rather than as a bare id. Songs already kept or already on their way
 * are left alone — a button that fetches forty songs again to add the one
 * that is missing is a button nobody presses twice.
 */
suspend fun downloadAll(
    context: Context,
    database: MusicDatabase,
    songs: List<MediaMetadata>,
    downloads: Map<String, Download>,
) {
    val wanted = songs
        .distinctBy { it.id }
        .filter {
            when (downloads[it.id]?.state) {
                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> false
                else -> true
            }
        }
    if (wanted.isEmpty()) return
    database.withTransaction { wanted.forEach { insert(it) } }
    wanted.forEach { song ->
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            DownloadRequest.Builder(song.id, song.id.toUri())
                .setCustomCacheKey(song.id)
                .setData(song.title.toByteArray())
                .build(),
            false,
        )
    }
}

/** Where a list of songs stands: all kept, some still coming, or not yet asked for. */
fun downloadStateOf(songIds: List<String>, downloads: Map<String, Download>): Int = when {
    songIds.isEmpty() -> Download.STATE_STOPPED
    songIds.all { downloads[it]?.state == Download.STATE_COMPLETED } -> Download.STATE_COMPLETED
    songIds.all {
        downloads[it]?.state in setOf(Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_COMPLETED)
    } -> Download.STATE_DOWNLOADING
    else -> Download.STATE_STOPPED
}
