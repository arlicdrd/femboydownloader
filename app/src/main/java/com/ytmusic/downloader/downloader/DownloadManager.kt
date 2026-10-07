package com.ytmusic.downloader.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

enum class AudioFormat(val ext: String, val ytdlpFormat: String) {
    MP3("mp3", "mp3"),
    M4A("m4a", "m4a"),
    FLAC("flac", "flac")
}

/**
 * Seal-pattern downloader: one yt-dlp call with `--extract-audio`.
 * yt-dlp auto-wires `--ffmpeg-location` to the bundled native binary
 * (see YoutubeDL.execute source), so extraction + conversion to the
 * target format happen in a single step.
 * YouTube Music URLs only (music.youtube.com / youtube.com/watch).
 */
class DownloadManager(private val context: Context) {

    fun download(videoId: String, format: AudioFormat, outDir: File): Flow<DownloadState> =
        callbackFlow {
            trySend(DownloadState.Started(0))
            val url = "https://music.youtube.com/watch?v=$videoId"

            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-f", "bestaudio/best")
                addOption("--extract-audio")
                addOption("--audio-format", format.ytdlpFormat)
                addOption("--audio-quality", "0")
                addOption("--no-embed-chapters")
                addOption("-o", File(outDir, "%(id)s.%(ext)s").absolutePath)
            }

            try {
                // Blocking call; progress callback runs on yt-dlp's stdout
                // reader thread — trySend is thread-safe.
                YoutubeDL.getInstance().execute(request, "dl-$videoId") { progress, _, _ ->
                    trySend(DownloadState.Downloading(progress.toInt().coerceIn(0, 100)))
                }
            } catch (e: Exception) {
                trySend(DownloadState.Error(e.message ?: "yt-dlp failed"))
                close()
                return@callbackFlow
            }

            val expected = File(outDir, "$videoId.${format.ext}")
            val result = when {
                expected.exists() -> expected
                else -> outDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith(videoId)
                }
            }
            if (result != null) trySend(DownloadState.Done(result))
            else trySend(DownloadState.Error("audio file not produced"))
            close()
        }.flowOn(Dispatchers.IO)

    /** Terminal + progress states consumed by TrackDownloadWorker notification. */
    sealed interface DownloadState {
        data class Started(val progress: Int) : DownloadState
        data class Downloading(val progress: Int) : DownloadState
        data class Converting(val progress: Int) : DownloadState
        data class Done(val file: File) : DownloadState
        data class Error(val message: String) : DownloadState
    }
}
