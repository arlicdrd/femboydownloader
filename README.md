# YTM Downloader — YouTube Music downloader & player

Kotlin + Jetpack Compose (Material 3) Android app. All content comes **only
from YouTube Music** (InnerTube `ANDROID_MUSIC` client, Metrolist pattern).
Downloads use the Seal pattern (yt-dlp `bestaudio` + native FFmpeg convert).
Lyrics use LrcLib (`lrclib.net/api/get` + `/api/search` fallback) — the same
provider family Metrolist and SongSync rely on — embedded as synced LRC.

## Features
- YouTube-Music-only search, streaming (Media3 ExoPlayer + MediaSessionService)
- Download track / playlist as **MP3 / M4A / FLAC**
- Full metadata: title, artist, album, **hi-res cover art**, **synced LRC lyrics**
  (ID3v2 USLT / MP4 ©lyr / Vorbis LYRICS via jaudiotagger)
- Background `TrackDownloadWorker` (+ `PlaylistDownloadWorker`) with
  notification + `MediaScannerConnection` indexing
- Share a `music.youtube.com` / `youtube.com` link into the app to download it

## Project layout (`ytmusic-downloader/`)
- `app/.../innertube/InnerTubeClient.kt` — search / next / player / browse
- `app/.../downloader/DownloadManager.kt` — yt-dlp + FFmpeg (`AudioFormat`)
- `app/.../lyrics/LyricsFetcher.kt` — LrcLib exact + fuzzy fallback
- `app/.../tags/AudioTagger.kt` — artwork + synced-lyrics embedding
- `app/.../worker/TrackDownloadWorker.kt`, `PlaylistDownloadWorker.kt`
- `app/.../playback/MusicService.kt` — Media3 session
- `app/.../ui/` — Compose Search / Downloads / Library + bottom nav
- `.github/workflows/build.yml` — CI builds the APK

## Build with GitHub Actions (recommended)
1. Push this folder to a GitHub repo (as the repo root, or keep the
   `ytmusic-downloader/` subdir and adjust `working-directory`).
2. `Actions` tab → **Build Android APK** → APK appears under Artifacts as
   `app-debug`. Tagged `v*` pushes also trigger the workflow.

> Local build needs Android Studio (or JDK 17 + Gradle 8.7 + Android SDK 34).
> Open `ytmusic-downloader/` and run `assembleDebug`. The bundled `gradlew`
> bootstraps Gradle if `gradle-wrapper.jar` is absent and no system gradle
> exists (Windows: install Gradle or use Android Studio).

## Notes / attributions
- Streaming/library concept: [Metrolist](https://github.com/MetrolistGroup/Metrolist) (InnerTube + Media3).
- Download engine concept: [Seal](https://github.com/JunkFood02/Seal) (yt-dlp + FFmpeg + metadata embedding).
- Lyrics concept: LrcLib, as used by Metrolist / [SongSync](https://github.com/Lambada10/SongSync).
- yt-dlp wrapper: `io.github.junkfood02.youtubedl-android:library:ffmpeg:0.17.3`
  (JunkFood02's maintained fork of yausername's wrapper, same `com.yausername.*`
  API — verified against source; yt-dlp auto-wires `--ffmpeg-location` to the
  bundled native binary, so `--extract-audio` converts in one step).
- jaudiotagger artifact is `net.jthink:jaudiotagger:3.0.1` (there is no
  `org.mordan:jaudiotagger`).
- InnerTube `apiKey`/`clientVersion` drift over time — if search breaks, pull
  fresh values from the Metrolist `innertube` module.
