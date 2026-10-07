# Keep youtubedl-android / ffmpeg native loaders
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
# jaudiotagger uses reflection over tag frames
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
