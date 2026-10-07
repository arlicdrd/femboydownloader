package com.ytmusic.downloader.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

enum class AudioFormat(val ext: String, val ffmpegArgs: Array<String>) {
    MP3("mp3", arrayOf("-codec:a", "libmp3lame", "-q:a", "0")),
    M4A("m4a", arrayOf("-codec:a", "aac", "-b:a", "256k")),
    FLAC("flac", arrayOf("-codec:a", "flac"))
}

/**
 * Seal-pattern downloader: yt-dlp extracts bestaudio, FFmpeg converts.
 * YouTube Music URLs only (music.youtube.com / youtube.com/watch).
 *
 * Progress is coarse-grained (yt-dlp callbacks are non-suspending, so exact
 * percentages can't be re-emitted from a Flow): Started -> Downloading ->
 * Converting -> Done/Error. WorkManager shows these in its notification.
 */
class DownloadManager(private val context: Context) {

    fun download(videoId: String, format: AudioFormat, outDir: File): Flow<DownloadState> = flow {
        emit(DownloadState.Started(0))
        val url = "https://music.youtube.com/watch?v=$videoId"

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--extract-audio")
            addOption("-f", "bestaudio/best")
            addOption("--audio-quality", "0")
            addOption("-o", File(outDir, "$videoId.%(ext)s").absolutePath)
        }

        emit(DownloadState.Downloading(10))
        try {
            YoutubeDL.getInstance().execute(request, "$videoId-dl")
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "yt-dlp failed"))
            return@flow
        }

        emit(DownloadState.Converting(80))
        val raw = outDir.listFiles()?.firstOrNull {
            it.name.startsWith(videoId) && it.isFile
        }
        if (raw == null) {
            emit(DownloadState.Error("audio stream not found"))
            return@flow
        }
        val dest = File(outDir, "$videoId.${format.ext}")
        try {
            val args = mutableListOf("-y", "-i", raw.absolutePath) +
                format.ffmpegArgs.toList() + listOf(dest.absolutePath)
            com.yausername.ffmpeg.FFmpeg.getInstance().execute(args.toTypedArray())
            if (raw.absolutePath != dest.absolutePath) runCatching { raw.delete() }
            emit(DownloadState.Done(dest))
        } catch (e: Exception) {
            emit(DownloadState.Error("convert failed: ${e.message}"))
        }
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
