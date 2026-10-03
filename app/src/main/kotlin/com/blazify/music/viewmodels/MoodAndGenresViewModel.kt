/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.viewmodels

import android.content.Context
import com.blazify.music.db.MusicDatabase
import com.blazify.music.utils.GenreTaste
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blazify.innertube.YouTube
import com.blazify.innertube.pages.MoodAndGenres
import com.blazify.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    /** Which tiles are genres, and how each scores for this listener. See [GenreTaste]. */
    val genreScores = GenreTaste.scores
    val genreTitles = GenreTaste.genreTitles

    init {
        viewModelScope.launch {
            YouTube
                .moodAndGenres()
                .onSuccess {
                    moodAndGenres.value = it
                    GenreTaste.refresh(context, database, it)
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
