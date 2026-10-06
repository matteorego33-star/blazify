/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blazify.innertube.YouTube
import com.blazify.innertube.models.filterYoutubeShorts
import com.blazify.innertube.pages.BrowseResult
import com.blazify.innertube.pages.HomePage
import com.blazify.music.constants.HideExplicitKey
import com.blazify.music.constants.HideVideoSongsKey
import com.blazify.music.constants.HideYoutubeShortsKey
import com.blazify.music.db.MusicDatabase
import com.blazify.music.utils.GenreTaste
import com.blazify.music.utils.dataStore
import com.blazify.music.utils.get
import com.blazify.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")
    /** The mood tile's name, when this page was opened from one. */
    private val title = savedStateHandle.get<String>("title")

    /** The tile's name, for the heading while YouTube's answer is still coming. */
    val pageTitle: String? = title

    /**
     * Whether this page will open on shelves of its own — a mood with a Home chip — or on a
     * grid of albums and playlists, as a genre and a mood with no chip do. Known before
     * anything arrives, so the placeholder can be drawn in the shape that is coming.
     */
    val expectShelves: Boolean =
        title != null && !GenreTaste.isGenre(title) &&
            (knownChips?.any { it.title.lowercase() == chipName(title) } ?: true)

    val result = MutableStateFlow<BrowseResult?>(null)

    /**
     * What YouTube Music puts together for this account in the mood — "Your
     * Relax mix", the songs played in that mood. These are the Home feed
     * filtered by the mood's chip, which is the one place YouTube's own
     * personalisation reaches a mood; the page's grid is editorial, the same
     * for everybody in the country, and in a country that gets little of its
     * own it is mostly somebody else's language. Genres have no chip and get
     * the grid alone.
     */
    val forYou = MutableStateFlow<List<HomePage.Section>>(emptyList())

    /**
     * True once both answers are in. The page waits for the pair, as the iPhone does: shown
     * as each arrived, the grid came first and the shelves then landed on top of it a moment
     * later, moving whatever was about to be tapped.
     */
    val ready = MutableStateFlow(false)
    private var gridDone = false
    private var shelvesDone = title == null

    private fun settle() {
        if (gridDone && shelvesDone) ready.value = true
    }

    init {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            YouTube
                .browse(browseId, params)
                .onSuccess {
                    result.value = it
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                }.onFailure {
                    reportException(it)
                }
            gridDone = true
            settle()
        }
        title?.let {
            loadForYou(it)
            // A genre opened by hand counts towards its place on Browse.
            viewModelScope.launch { GenreTaste.noteOpened(context, database, it) }
        }
    }

    private fun loadForYou(moodTitle: String) {
        viewModelScope.launch {
            try {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val wanted = chipName(moodTitle)
                // The chips are the same all session; asked for once, kept, so every mood page
                // after the first is one request to YouTube instead of two in a row.
                val chips = knownChips ?: YouTube.home().getOrNull()?.chips?.also { knownChips = it }
                val chip = chips
                    ?.firstOrNull { it.title.lowercase() == wanted }
                    ?: return@launch
                val chipParams = chip.endpoint?.params ?: return@launch
                YouTube.home(params = chipParams)
                    .onSuccess { page ->
                        forYou.value = page.sections
                            .map { section ->
                                if (hideExplicit) section.copy(items = section.items.filter { !it.explicit }) else section
                            }
                            .filter { it.items.isNotEmpty() }
                    }.onFailure { reportException(it) }
            } finally {
                shelvesDone = true
                settle()
            }
        }
    }

    /** The Home chip a mood tile answers to. Most share a name; two do not. */
    private fun chipName(moodTitle: String): String =
        when (moodTitle.lowercase()) {
            "chill" -> "relax"
            "energy boosters" -> "energize"
            else -> moodTitle.lowercase()
        }

    companion object {
        @Volatile
        private var knownChips: List<HomePage.Chip>? = null

        fun rememberChips(chips: List<HomePage.Chip>) {
            if (chips.isNotEmpty()) knownChips = chips
        }
    }
}
