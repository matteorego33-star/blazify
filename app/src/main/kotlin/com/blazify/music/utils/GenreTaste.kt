/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import android.content.Context
import com.blazify.innertube.YouTube
import com.blazify.innertube.models.AlbumItem
import com.blazify.innertube.models.ArtistItem
import com.blazify.innertube.models.PlaylistItem
import com.blazify.innertube.models.SongItem
import com.blazify.innertube.models.YTItem
import com.blazify.innertube.pages.MoodAndGenres
import com.blazify.music.constants.GenreTasteArtistsKey
import com.blazify.music.constants.GenreTasteGenresKey
import com.blazify.music.constants.GenreTasteOpenedKey
import com.blazify.music.constants.GenreTasteScannedAtKey
import com.blazify.music.db.MusicDatabase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDateTime

/**
 * Which genre tiles are this listener's.
 *
 * YouTube's list of genres is the same for everybody in the country — in Nepal it runs to
 * Kannada, Malayalam and Marathi whether or not anybody in the house has ever played a note of
 * them — and it comes in no order but the alphabet. YouTube keeps no "for you" ordering of it to
 * ask for. But each genre's page carries fifty of its songs with their artists, and the player
 * keeps a history of who was played: a genre whose page names the listener's artists is theirs,
 * and one that names none of them is not.
 *
 * So the genre pages are read through once a week, quietly, on the same footing as the covers
 * for the Browse tiles, and each genre is scored by how many plays of the listener's artists its
 * page accounts for. What the listener actually opens counts too, so a genre they go to by hand
 * rises without having to be deduced. Moods are left alone: Chill is for everybody.
 */
object GenreTaste {
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /** One tap on a tile is worth this many plays of an artist found there. */
    private const val OPEN_WEIGHT = 4.0

    /** How many of the listener's artists are matched against each page. */
    private const val ARTISTS_CONSIDERED = 40

    /** Score per genre title. Genres without one keep YouTube's order, after those with. */
    val scores = MutableStateFlow<Map<String, Double>>(emptyMap())

    /** The titles that are genres rather than moods, learned from the Moods & genres page. */
    val genreTitles = MutableStateFlow<Set<String>>(emptySet())

    private var loaded = false
    private var scanning = false
    private var artistsByGenre: Map<String, Set<String>> = emptyMap()
    private var opened: Map<String, Double> = emptyMap()
    private var scannedAt = 0L

    fun isGenre(title: String) = title in genreTitles.value

    /**
     * The tiles with the listener's genres first: moods as given, then genres with a score by
     * score, then the rest in the order YouTube gave.
     */
    fun <T> order(items: List<T>, title: (T) -> String): List<T> {
        val scored = scores.value
        val genres = genreTitles.value
        val moods = items.filter { title(it) !in genres }
        val ranked =
            items.withIndex()
                .filter { title(it.value) in genres }
                .sortedWith(
                    compareByDescending<IndexedValue<T>> { scored[title(it.value)] ?: 0.0 }
                        .thenBy { it.index },
                ).map { it.value }
        return moods + ranked
    }

    /** How many of these are the listener's genres — those with any score at all. */
    fun <T> count(items: List<T>, title: (T) -> String): Int =
        items.count { title(it) in genreTitles.value && (scores.value[title(it)] ?: 0.0) > 0 }

    /** A tile opened by hand. Counts whether or not its page is ever read. */
    suspend fun noteOpened(context: Context, database: MusicDatabase, title: String) {
        load(context)
        if (title !in genreTitles.value) return
        opened = opened + (title to (opened[title] ?: 0.0) + 1)
        context.safeDataStoreEdit { prefs ->
            prefs[GenreTasteOpenedKey] = opened.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        }
        rescore(database)
    }

    /**
     * Learns which tiles are genres from the page's sections, reads the genre pages through if
     * the last read is a week old — two at a time, so they never crowd out a song that is
     * loading — and scores them against the listener's history.
     */
    suspend fun refresh(context: Context, database: MusicDatabase, sections: List<MoodAndGenres>) {
        load(context)
        // The page is two grids: moods and moments first, genres last. The heading says which,
        // and the position is the fallback when the heading is in a language this does not read.
        val genreSection =
            sections.firstOrNull { it.title.contains("genre", ignoreCase = true) } ?: sections.lastOrNull() ?: return
        val genres = genreSection.items
        if (genres.isEmpty()) return
        val titles = genres.map { it.title }.toSet()
        if (titles != genreTitles.value) {
            genreTitles.value = titles
            context.safeDataStoreEdit { prefs -> prefs[GenreTasteGenresKey] = titles.joinToString("\n") }
        }
        rescore(database)

        val fresh = System.currentTimeMillis() - scannedAt < KEEP_MS
        if (scanning || (fresh && titles.all { it in artistsByGenre })) return
        scanning = true
        try {
            val found = HashMap<String, Set<String>>()
            val gate = Semaphore(2)
            coroutineScope {
                genres.forEach { genre ->
                    launch {
                        val names =
                            gate.withPermit {
                                YouTube.browse(genre.endpoint.browseId, genre.endpoint.params).getOrNull()
                                    ?.items?.flatMap { it.items }?.let(::artistNames)
                            } ?: return@launch
                        synchronized(found) { found[genre.title] = names }
                    }
                }
            }
            if (found.isEmpty()) return
            artistsByGenre = artistsByGenre + found
            scannedAt = System.currentTimeMillis()
            context.safeDataStoreEdit { prefs ->
                prefs[GenreTasteArtistsKey] =
                    artistsByGenre.entries.joinToString("\n") { "${it.key}\t${it.value.joinToString("|")}" }
                prefs[GenreTasteScannedAtKey] = scannedAt
            }
            rescore(database)
        } finally {
            scanning = false
        }
    }

    /** What was kept from last time, read once. */
    private suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.dataStore.data.first()
        scannedAt = prefs[GenreTasteScannedAtKey] ?: 0L
        genreTitles.value = prefs[GenreTasteGenresKey].orEmpty().lineSequence().filter { it.isNotBlank() }.toSet()
        artistsByGenre =
            prefs[GenreTasteArtistsKey].orEmpty().lineSequence()
                .mapNotNull { line ->
                    line.split('\t').takeIf { it.size == 2 }
                        ?.let { it[0] to it[1].split('|').filter(String::isNotBlank).toSet() }
                }.toMap()
        opened =
            prefs[GenreTasteOpenedKey].orEmpty().lineSequence()
                .mapNotNull { line ->
                    line.split('\t').takeIf { it.size == 2 }?.let { pair -> pair[1].toDoubleOrNull()?.let { pair[0] to it } }
                }.toMap()
    }

    /**
     * Each genre's score: the plays of the listener's artists that its page names, plus the
     * tiles opened by hand.
     */
    private suspend fun rescore(database: MusicDatabase) {
        val top =
            database.mostPlayedArtists(LocalDateTime.of(1970, 1, 1, 0, 0), limit = ARTISTS_CONSIDERED).first()
                .associate { it.artist.name.lowercase() to it.songCount.toDouble() }
        val out = HashMap<String, Double>()
        for ((genre, names) in artistsByGenre) {
            val score = top.entries.sumOf { (name, plays) -> if (name in names) plays else 0.0 }
            if (score > 0) out[genre] = score
        }
        for ((genre, times) in opened) {
            out[genre] = (out[genre] ?: 0.0) + times * OPEN_WEIGHT
        }
        scores.value = out
    }

    /** The artists a page's items name, lowercased. */
    private fun artistNames(items: List<YTItem>): Set<String> {
        val names = HashSet<String>()
        for (item in items) {
            when (item) {
                is SongItem -> item.artists.forEach { names.add(it.name.lowercase()) }
                is AlbumItem -> item.artists?.forEach { names.add(it.name.lowercase()) }
                is PlaylistItem -> item.author?.let { names.add(it.name.lowercase()) }
                is ArtistItem -> names.add(item.title.lowercase())
                else -> Unit
            }
        }
        return names
    }
}
