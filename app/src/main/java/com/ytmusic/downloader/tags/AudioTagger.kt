package com.ytmusic.downloader.tags

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val coverArtUrl: String? = null,
    /** Timestamped LRC string, e.g. "[00:12.34] line". Written to USLT/SYLT (MP3), (c)lyr (M4A), LYRICS (FLAC). */
    val syncedLyricsLrc: String? = null,
    val plainLyrics: String? = null
)

/**
 * Embeds complete metadata + synced lyrics + cover art (SongSync behavior,
 * applied automatically right after download).
 *
 * MP3  -> ID3v2.3: TIT2/TPE1/TALB/APIC + USLT (plain) and SYLT-compatible LRC dump
 * M4A  -> MP4 atoms: (c)nam/(c)ART/(c)alb/covr/(c)lyr
 * FLAC -> Vorbis comments: TITLE/ARTIST/ALBUM/LYRICS + METADATA_BLOCK_PICTURE
 * (jaudiotagger abstracts the container-specific frames.)
 */
object AudioTagger {

    suspend fun embedMetadata(file: File, metadata: AudioMetadata) = withContext(Dispatchers.IO) {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault

        tag.setField(FieldKey.TITLE, metadata.title)
        tag.setField(FieldKey.ARTIST, metadata.artist)
        if (metadata.album.isNotBlank()) tag.setField(FieldKey.ALBUM, metadata.album)
        metadata.genre?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
        metadata.year?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.YEAR, it) }
        metadata.trackNumber?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TRACK, it) }

        // Lyrics: prefer synced LRC (players that support SYLT/LRC show karaoke),
        // fall back to plain text. jaudiotagger maps FieldKey.LYRICS to
        // USLT (MP3) / (c)lyr (M4A) / LYRICS (FLAC).
        val lyrics = metadata.syncedLyricsLrc?.takeIf { it.isNotBlank() }
            ?: metadata.plainLyrics?.takeIf { it.isNotBlank() }
        if (lyrics != null) {
            runCatching { tag.setField(FieldKey.LYRICS, lyrics) }
        }

        // High-res cover art download -> APIC/covr/PICTURE
        metadata.coverArtUrl?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                val bytes: ByteArray = HttpClient(Android).use { it.get(url).body() }
                val art = ArtworkFactory.getNew()
                art.binaryData = bytes
                art.mimeType = if (url.contains(".png", true)) "image/png" else "image/jpeg"
                art.description = "cover"
                tag.deleteArtworkField()
                tag.addField(art)
            }
        }

        audio.commit()
    }
}
