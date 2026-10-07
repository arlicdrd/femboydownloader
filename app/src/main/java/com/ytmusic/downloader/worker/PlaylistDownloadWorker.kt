package com.ytmusic.downloader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytmusic.downloader.downloader.AudioFormat
import com.ytmusic.downloader.innertube.InnerTubeClient

/** Enqueues one TrackDownloadWorker per playlist entry (YouTube Music only). */
class PlaylistDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val playlistId = inputData.getString(KEY_PLAYLIST) ?: return Result.failure()
        val format = AudioFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "MP3")
        return try {
            val tube = InnerTubeClient()
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val pl = tube.playlist(browseId)
            pl.songs.forEach { song ->
                TrackDownloadWorker.enqueue(applicationContext, song.videoId, format, song.title)
            }
            Result.success(workDataOf("count" to pl.songs.size, "title" to pl.title))
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_PLAYLIST = "playlistId"
        const val KEY_FORMAT = "format"
    }
}
