/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.screens

import android.net.Uri
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.blazify.music.LocalPlayerAwareWindowInsets
import com.blazify.music.R
import com.blazify.music.ui.component.IconButton
import com.blazify.music.ui.component.NavigationTitle
import com.blazify.music.ui.component.shimmer.ListItemPlaceHolder
import com.blazify.music.ui.component.shimmer.ShimmerHost
import com.blazify.music.ui.utils.backToMain
import com.blazify.music.viewmodels.MoodAndGenresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = if (localConfiguration.orientation == ORIENTATION_LANDSCAPE) 3 else 2

    val moodAndGenresList by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val genreScores by viewModel.genreScores.collectAsStateWithLifecycle()
    val genreTitles by viewModel.genreTitles.collectAsStateWithLifecycle()
    var allGenres by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        if (moodAndGenresList == null) {
            item(key = "mood_and_genres_shimmer") {
                ShimmerHost(
                    modifier = Modifier.animateItem()
                ) {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }

        moodAndGenresList?.forEachIndexed { index, moodAndGenres ->
            // The genres come in the listener's order — the ones their history
            // points to first — and only their own and a couple more are out,
            // with the rest behind a line. Moods keep YouTube's order and are
            // all shown; Chill is for everybody.
            val isGenres = moodAndGenres.items.any { it.title in genreTitles } && moodAndGenres.items.all { it.title in genreTitles }
            val ordered =
                if (isGenres) {
                    moodAndGenres.items.withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<com.blazify.innertube.pages.MoodAndGenres.Item>> { genreScores[it.value.title] ?: 0.0 }
                                .thenBy { it.index },
                        ).map { it.value }
                } else {
                    moodAndGenres.items
                }
            val own = if (isGenres) ordered.count { (genreScores[it.title] ?: 0.0) > 0 } else 0
            val shown = if (isGenres && !allGenres) ordered.take(maxOf(6, own + 2)) else ordered
            item(key = "mood_and_genres_section_$index") {
                Column(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 6.dp),
                ) {
                    NavigationTitle(
                        title = moodAndGenres.title,
                    )
                    shown.chunked(itemsPerRow).forEach { row ->
                        Row {
                            row.forEach {
                                MoodAndGenresButton(
                                    title = it.title,
                                    onClick = {
                                        navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}&title=${Uri.encode(it.title)}")
                                    },
                                    modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(6.dp),
                                )
                            }

                            repeat(itemsPerRow - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (isGenres && ordered.size > maxOf(6, own + 2)) {
                        Text(
                            text = stringResource(if (allGenres) R.string.fewer_genres else R.string.all_genres),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { allGenres = !allGenres }
                                    .padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.mood_and_genres)) },
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

@Composable
fun MoodAndGenresButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
        modifier
            .height(MoodAndGenresButtonHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

val MoodAndGenresButtonHeight = 48.dp
