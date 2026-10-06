/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.screens

import com.blazify.music.ui.component.shimmer.CardGridPlaceholder
import com.blazify.music.ui.component.shimmer.ShelvesPlaceholder
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.blazify.music.constants.ListItemHeight
import com.blazify.music.ui.component.YouTubeListItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.ExperimentalFoundationApi
import com.blazify.music.ui.component.NavigationTitle
import com.blazify.music.playback.queues.ListQueue
import com.blazify.music.extensions.toMediaItem
import com.blazify.innertube.models.YTItem
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.blazify.innertube.models.AlbumItem
import com.blazify.innertube.models.ArtistItem
import com.blazify.innertube.models.EpisodeItem
import com.blazify.innertube.models.PlaylistItem
import com.blazify.innertube.models.PodcastItem
import com.blazify.innertube.models.SongItem
import com.blazify.music.LocalPlayerAwareWindowInsets
import com.blazify.music.LocalPlayerConnection
import com.blazify.music.R
import com.blazify.music.constants.GridItemSize
import com.blazify.music.constants.GridItemsSizeKey
import com.blazify.music.constants.GridThumbnailHeight
import com.blazify.music.models.toMediaMetadata
import com.blazify.music.playback.queues.YouTubeQueue
import com.blazify.music.ui.component.IconButton
import com.blazify.music.ui.component.LocalMenuState
import com.blazify.music.ui.component.YouTubeGridItem
import com.blazify.music.ui.component.shimmer.GridItemPlaceHolder
import com.blazify.music.ui.component.shimmer.ShimmerHost
import com.blazify.music.ui.menu.YouTubeAlbumMenu
import com.blazify.music.ui.menu.YouTubeArtistMenu
import com.blazify.music.ui.menu.YouTubePlaylistMenu
import com.blazify.music.ui.menu.YouTubeSongMenu
import com.blazify.music.ui.utils.backToMain
import com.blazify.music.utils.rememberEnumPreference
import com.blazify.music.viewmodels.YouTubeBrowseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val browseResult by viewModel.result.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val allItems = browseResult?.items?.flatMap { it.items } ?: emptyList()
    val forYou by viewModel.forYou.collectAsStateWithLifecycle()

    // One card, however it is laid out: the same taps and the same menus in
    // the grid and in the rails above it.
    @Composable
    fun browseItem(item: YTItem, fillMaxWidth: Boolean, modifier: Modifier, square: Boolean = false) {
        YouTubeGridItem(
            item = item,
            // In a rail, songs are square like everything beside them, as on Home and the
            // iPhone. At the default 16:9 a song drew so large it covered its own heading.
            thumbnailRatio = if (square || item !is SongItem) 1f else 16f / 9,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            fillMaxWidth = fillMaxWidth,
            coroutineScope = coroutineScope,
            modifier =
                modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata()),
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                }

                                is PodcastItem -> {
                                    navController.navigate("online_podcast/${item.id}")
                                }

                                is EpisodeItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata()),
                                        )
                                    }
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                when (item) {
                                    is SongItem -> {
                                        YouTubeSongMenu(
                                            song = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is AlbumItem -> {
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is ArtistItem -> {
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is PlaylistItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is PodcastItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item.asPlaylistItem(),
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }

                                    is EpisodeItem -> {
                                        YouTubeSongMenu(
                                            song = item.asSongItem(),
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            }
                        },
                    ),
        )
    }

    val playlistsTitle = stringResource(R.string.playlists)

    /**
     * One shelf under its heading: songs as a list four to a column, as Quick picks reads,
     * everything else as square cards in a row.
     */
    fun LazyGridScope.shelf(key: String, title: String, label: String?, items: List<YTItem>) {
        item(key = "${key}_title", span = { GridItemSpan(maxLineSpan) }) {
            val songs = items.filterIsInstance<SongItem>()
            NavigationTitle(
                title = title,
                label = label,
                onPlayAllClick =
                    if (songs.isNotEmpty()) {
                        {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = title,
                                    items = songs.map { it.toMediaMetadata().toMediaItem() },
                                ),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
        val songsOnly = items.all { it is SongItem }
        item(key = "${key}_rail", span = { GridItemSpan(maxLineSpan) }) {
            if (songsOnly) {
                // A shelf of songs reads as a list — title, artist, a small cover — four
                // to a column, as Quick picks does on Home and on the iPhone.
                val rowWidth = LocalConfiguration.current.screenWidthDp.dp * 0.9f
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4),
                ) {
                    items(
                        items = items.filterIsInstance<SongItem>().distinctBy { it.id },
                        key = { "${key}_${it.id}" },
                    ) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            isSwipeable = false,
                            modifier =
                                Modifier
                                    .width(rowWidth)
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(song = song, onDismiss = menuState::dismiss)
                                            }
                                        },
                                    ),
                        )
                    }
                }
            } else {
                // Big cards, two and a bit across, as on the iPhone: a mix is chosen by its
                // cover, and a cover the size of a stamp is hard to choose by.
                val cardWidth = LocalConfiguration.current.screenWidthDp.dp * 0.42f
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    items(
                        items = items.distinctBy { it.id },
                        key = { "${key}_${it.id}" },
                    ) { item ->
                        browseItem(item, fillMaxWidth = true, modifier = Modifier.width(cardWidth), square = true)
                    }
                }
            }
        }
    }

    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // What YouTube Music puts together for this account in the mood, above
        // the editorial grid — the shelves the Home chip of the same mood
        // carries. See the view model for why the grid alone was not enough.
        if (ready) forYou.forEachIndexed { index, section ->
            shelf("for_you_$index", section.title, section.label, section.items)
        }

        // The page's own sections. A genre page is several shelves — songs, albums,
        // playlists — and drawn as one grid it put songs in as wide video frames with
        // their titles across the pictures. A section of playlists alone stays a grid,
        // as a mood's list of playlists always was.
        if (ready) browseResult?.items.orEmpty().forEachIndexed { index, section ->
            val cards = section.items.distinctBy { it.id }
            if (cards.isEmpty()) return@forEachIndexed
            if (cards.all { it is PlaylistItem }) {
                val heading = section.title ?: if (forYou.isNotEmpty()) playlistsTitle else null
                if (heading != null) {
                    item(key = "browse_grid_title_$index", span = { GridItemSpan(maxLineSpan) }) {
                        NavigationTitle(title = heading)
                    }
                }
                items(items = cards, key = { "yt_browse_${index}_${it.id}" }) { item ->
                    browseItem(item, fillMaxWidth = true, modifier = Modifier.animateItem())
                }
            } else {
                shelf("browse_$index", section.title.orEmpty(), null, cards)
            }
        }

        if (!ready) {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                if (viewModel.expectShelves) ShelvesPlaceholder() else CardGridPlaceholder()
            }
        } else if (allItems.isEmpty() && forYou.isEmpty()) {
            // An answer that holds nothing says so, rather than leaving a page
            // with nothing written on it.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp, start = 32.dp, end = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    TopAppBar(
        // The tile's own name at once; YouTube's, if it gives one, when it comes.
        title = { Text(browseResult?.title ?: viewModel.pageTitle.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
