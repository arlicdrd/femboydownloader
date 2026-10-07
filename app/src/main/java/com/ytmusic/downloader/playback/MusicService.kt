package com.ytmusic.downloader.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ytmusic.downloader.innertube.InnerTubeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media3 playback service (Metrolist pattern): streams resolve via InnerTube,
 * downloads play directly from local files.
 */
class MusicService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tube = InnerTubeClient()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    /** Resolve a YouTube Music videoId to a playable stream URL. */
    fun resolveAndPlay(videoId: String) {
        val player = session?.player ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val url = tube.streamUrl(videoId)
                launch(Dispatchers.Main) {
                    player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
