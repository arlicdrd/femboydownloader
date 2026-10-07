package com.ytmusic.downloader.worker

import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytmusic.downloader.downloader.AudioFormat
import com.ytmusic.downloader.downloader.DownloadManager
import com.ytmusic.downloader.innertube.InnerTubeClient
import com.ytmusic.downloader.lyrics.LyricsFetcher
import com.ytmusic.downloader.tags.AudioMetadata
import com.ytmusic.downloader.tags.AudioTagger
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * PROMPT 4 — complete download pipeline for one YouTube Music track:
 * 1. InnerTube metadata + hi-res cover
 * 2. yt-dlp bestaudio -> FFmpeg convert (MP3/M4A/FLAC)
 * 3. LrcLib synced LRC fetch
 * 4. jaudiotagger embed (title/artist/album/art/lyrics)
 * 5. MediaScanner index + progress notification
 */
class TrackDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val format = AudioFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "MP3")
        val titleHint = inputData.getString(KEY_TITLE).orEmpty()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val notifId = videoId.hashCode()
        fun progress(text: String, pct: Int) {
            val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(titleHint.ifBlank { videoId })
                .setContentText(text)
                .setProgress(100, pct, pct <= 0)
                .setOngoing(true)
                .build()
            nm?.notify(notifId, n)
        }
        progress("Fetching info…", 0)

        return try {
            val tube = InnerTubeClient()
            val info = tube.next(videoId)
            progress("Downloading audio…", 10)

            val musicDir = File(applicationContext.getExternalFilesDir(null), "Music").apply { mkdirs() }
            val dm = DownloadManager(applicationContext)
            var rawFile: File? = null
            var failed: String? = null
            dm.download(videoId, format, musicDir).collect { s ->
                when (s) {
                    is DownloadManager.DownloadState.Downloading -> progress("Downloading audio…", s.progress)
                    is DownloadManager.DownloadState.Converting -> progress("Converting…", s.progress)
                    is DownloadManager.DownloadState.Done -> rawFile = s.file
                    is DownloadManager.DownloadState.Error -> failed = s.message
                    else -> {}
                }
            }
            val local = rawFile ?: return Result.failure(workDataOf("error" to (failed ?: "download failed")))

            progress("Fetching synced lyrics…", 85)
            val lyrics = LyricsFetcher().fetch(
                info.title, info.artist, info.album, info.durationSec
            )

            progress("Writing tags…", 92)
            // Final user-visible name: "Artist - Title.ext"
            val safeArtist = info.artist.ifBlank { "Unknown" }.sanitize()
            val safeTitle = info.title.ifBlank { videoId }.sanitize()
            val final = File(musicDir, "$safeArtist - $safeTitle.${format.ext}")
            runCatching { if (final.exists()) final.delete() }
            local.renameTo(final)

            AudioTagger.embedMetadata(
                final,
                AudioMetadata(
                    title = info.title,
                    artist = info.artist,
                    album = info.album.ifBlank { "YouTube Music" },
                    coverArtUrl = info.artworkUrl,
                    syncedLyricsLrc = lyrics.syncedLrc,
                    plainLyrics = lyrics.plain
                )
            )

            MediaScannerConnection.scanFile(applicationContext, arrayOf(final.absolutePath), null, null)
            progress("Done", 100)
            nm?.cancel(notifId)
            Result.success(Data.Builder().putString("file", final.absolutePath).build())
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun String.sanitize() = replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)

    companion object {
        const val CHANNEL_ID = "downloads"
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_FORMAT = "format"
        const val KEY_TITLE = "title"

        fun enqueue(context: Context, videoId: String, format: AudioFormat, title: String) {
            val req = OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_VIDEO_ID to videoId,
                        KEY_FORMAT to format.name,
                        KEY_TITLE to title
                    )
                )
                .addTag("dl-$videoId")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("track-$videoId", ExistingWorkPolicy.KEEP, req)
        }
    }
}
