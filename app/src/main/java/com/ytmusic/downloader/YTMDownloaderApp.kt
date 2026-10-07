package com.ytmusic.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.ytmusic.downloader.worker.TrackDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YTMDownloaderApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@YTMDownloaderApp)
                // Seal-style: update yt-dlp binary on first launch (kept async, non-blocking)
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(this@YTMDownloaderApp) }
            } catch (_: YoutubeDLException) {
            }
            try {
                com.yausername.ffmpeg.FFmpeg.getInstance().init(this@YTMDownloaderApp)
            } catch (_: Exception) {
            }
        }
    }

    private fun createChannels() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                TrackDownloadWorker.CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        mgr.createNotificationChannel(
            NotificationChannel("playback", "Playback", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
