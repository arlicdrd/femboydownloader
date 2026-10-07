package com.ytmusic.downloader.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Synced-lyrics provider.
 * Primary: LrcLib (lrclib.net) — same provider used by Metrolist & SongSync fallback.
 * GET https://lrclib.net/api/get?track_name=&artist_name=&album_name=&duration=
 * Fallback: /api/search when exact match misses.
 */
class LyricsFetcher {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetch(
        track: String,
        artist: String,
        album: String,
        durationSec: Long?
    ): LyricResult {
        // 1) exact match
        runCatching {
            val hit: LrcLibHit = http.get("https://lrclib.net/api/get") {
                parameter("track_name", track)
                parameter("artist_name", artist)
                if (album.isNotBlank()) parameter("album_name", album)
                if (durationSec != null) parameter("duration", durationSec)
            }.body()
            if (!hit.syncedLyrics.isNullOrBlank() || !hit.plainLyrics.isNullOrBlank()) {
                return LyricResult(hit.syncedLyrics, hit.plainLyrics, "lrclib:get")
            }
        }
        // 2) fuzzy search fallback, pick closest duration (±10s) else first synced hit
        runCatching {
            val hits: List<LrcLibHit> = http.get("https://lrclib.net/api/search") {
                parameter("track_name", track)
                parameter("artist_name", artist)
                if (album.isNotBlank()) parameter("album_name", album)
            }.body()
            val withSynced = hits.filter { !it.syncedLyrics.isNullOrBlank() }
            val best = durationSec?.let { d ->
                (withSynced.ifEmpty { hits }).minByOrNull {
                    kotlin.math.abs((it.duration ?: d) - d)
                }
            } ?: withSynced.firstOrNull() ?: hits.firstOrNull()
            if (best != null) return LyricResult(best.syncedLyrics, best.plainLyrics, "lrclib:search")
        }
        return LyricResult(null, null, "none")
    }
}

@Serializable
private data class LrcLibHit(
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Long? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)

data class LyricResult(
    /** Timestamped LRC: lines like [mm:ss.xx] lyric */
    val syncedLrc: String?,
    val plain: String?,
    val source: String
)
