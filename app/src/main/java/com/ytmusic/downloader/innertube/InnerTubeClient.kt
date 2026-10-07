package com.ytmusic.downloader.innertube

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Minimal InnerTube client ported from the Metrolist `innertube` module idea:
 * YouTube Music only (music.youtube.com / ANDROID_MUSIC client).
 *
 * Only this source is used for library + streaming. No other extractor.
 */
class InnerTubeClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.NONE }
    }

    private val apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private val clientVersion = "7.02.51"
    private val userAgent = "com.google.android.apps.youtube.music/7.02.51 (Linux; U; Android 14)"

    suspend fun search(query: String, filter: String? = null): List<YTSong> {
        val body: JsonObject = http.post("https://music.youtube.com/youtubei/v1/search?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "context" to clientContext(),
                    "query" to query,
                    "params" to filter
                )
            )
        }.body()
        return parseSearch(body)
    }

    suspend fun next(videoId: String, playlistId: String? = null): TrackDetails {
        val payload = mutableMapOf<String, Any?>(
            "context" to clientContext(),
            "videoId" to videoId
        )
        if (playlistId != null) payload["playlistId"] = playlistId
        val body: JsonObject = http.post("https://music.youtube.com/youtubei/v1/next?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
        return parseNext(videoId, body)
    }

    /** Direct stream URL for ExoPlayer. InnerTube player endpoint. */
    suspend fun streamUrl(videoId: String): String {
        val body: JsonObject = http.post("https://music.youtube.com/youtubei/v1/player?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "context" to clientContext(),
                    "videoId" to videoId
                )
            )
        }.body()
        val formats = body["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray ?: throw IllegalStateException("no streams")
        // Manual extraction avoids serializer mismatch (bitrate can be String or Int).
        val audio = formats.mapNotNull { elem ->
            val obj = elem.jsonObject
            val mime = obj["mimeType"]?.jsonPrimitive?.content?.toString() ?: ""
            if (!mime.startsWith("audio/")) return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content?.toString() ?: ""
            val bitrate = obj["bitrate"]?.jsonPrimitive?.content?.toString()?.toLongOrNull()
            if (url.isNotBlank()) StreamFormat(mime, bitrate?.toInt(), url) else null
        }.maxByOrNull { it.bitrate ?: 0 }
            ?: throw IllegalStateException("no audio stream")
        return audio.url
    }

    suspend fun playlist(browseId: String): YTPlaylist {
        // browseId like VLPLxxxx or PLxxxx
        val body: JsonObject = http.post("https://music.youtube.com/youtubei/v1/browse?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("context" to clientContext(), "browseId" to browseId))
        }.body()
        return parsePlaylist(browseId, body)
    }

    private fun clientContext() = mapOf(
        "client" to mapOf(
            "clientName" to "ANDROID_MUSIC",
            "clientVersion" to clientVersion,
            "androidSdkVersion" to 34,
            "userAgent" to userAgent,
            "hl" to "en",
            "gl" to "US"
        )
    )

    // ---- minimal parsers (robust against InnerTube shape changes) ----

    private fun parseSearch(root: JsonObject): List<YTSong> {
        val out = mutableListOf<YTSong>()
        fun walk(o: JsonObject) {
            o["musicShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray?.forEach { item ->
                val mrl = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                val videoId = mrl["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                    ?: mrl["navigationEndpoint"]?.jsonObject
                        ?.get("watchEndpoint")?.jsonObject
                        ?.get("videoId")?.jsonPrimitive?.content
                    ?: return@forEach
                val flex = mrl["flexColumns"]?.jsonArray ?: return@forEach
                val texts = flex.mapNotNull {
                    it.jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString(" ") { r -> r.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                }
                val title = texts.getOrNull(0).orEmpty()
                val artist = texts.getOrNull(1)?.split("•")?.firstOrNull()?.trim().orEmpty()
                val album = texts.getOrNull(2)?.trim().orEmpty()
                val thumbs = mrl["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                    ?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.content }
                val durationSec = mrl["lengthText"]?.jsonObject?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?.let { parseDuration(it) }
                if (title.isNotBlank()) {
                    out += YTSong(videoId, title, artist, album, thumbs?.lastOrNull(), durationSec)
                }
            }
            o.values.forEach { v ->
                when (v) {
                    is JsonObject -> walk(v)
                    is kotlinx.serialization.json.JsonArray -> v.forEach { if (it is JsonObject) walk(it) }
                    else -> {}
                }
            }
        }
        runCatching { walk(root) }
        return out.distinctBy { it.videoId }.take(30)
    }

    private fun parseNext(videoId: String, root: JsonObject): TrackDetails {
        val details = root["playerResponse"]?.jsonObject
            ?.get("videoDetails")?.jsonObject
            ?: root["videoDetails"]?.jsonObject
        val title = details?.get("title")?.jsonPrimitive?.content ?: videoId
        val author = details?.get("author")?.jsonPrimitive?.content.orEmpty()
        val length = details?.get("lengthSeconds")?.jsonPrimitive?.content?.toLongOrNull()
        val thumbs = details?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.content }
        val hires = thumbs?.lastOrNull()?.replace("w60-h60", "w1024-h1024")
        return TrackDetails(videoId, title, author, "", hires, length)
    }

    private fun parsePlaylist(browseId: String, root: JsonObject): YTPlaylist {
        val header = root["header"]?.jsonObject?.get("musicDetailHeaderRenderer")?.jsonObject
        val title = header?.get("title")?.jsonObject?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: browseId
        val songs = mutableListOf<YTSong>()
        fun walk(o: JsonObject) {
            o["musicPlaylistShelfRenderer"]?.jsonObject?.get("contents")?.jsonArray?.forEach { item ->
                val mrl = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: return@forEach
                val vid = mrl["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
                    ?: return@forEach
                val flex = mrl["flexColumns"]?.jsonArray ?: return@forEach
                val texts = flex.mapNotNull {
                    it.jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString("") { r -> r.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                }
                songs += YTSong(vid, texts.getOrNull(0).orEmpty(), texts.getOrNull(1).orEmpty(), title, null, null)
            }
            o.values.forEach { v ->
                when (v) {
                    is JsonObject -> walk(v)
                    is kotlinx.serialization.json.JsonArray -> v.forEach { if (it is JsonObject) walk(it) }
                    else -> {}
                }
            }
        }
        runCatching { walk(root) }
        return YTPlaylist(browseId, title, songs)
    }

    private fun parseDuration(s: String): Long? {
        val p = s.split(":").mapNotNull { it.toLongOrNull() }
        return when (p.size) {
            2 -> p[0] * 60 + p[1]
            3 -> p[0] * 3600 + p[1] * 60 + p[2]
            else -> null
        }
    }
}

private data class StreamFormat(val mimeType: String, val bitrate: Int?, val url: String)

data class YTSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val durationSec: Long?
) {
    val watchUrl: String get() = "https://music.youtube.com/watch?v=$videoId"
}

data class TrackDetails(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val durationSec: Long?
)

data class YTPlaylist(val id: String, val title: String, val songs: List<YTSong>)
