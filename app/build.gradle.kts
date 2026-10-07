plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.ytmusic.downloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ytmusic.downloader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // youtubedl-android + ffmpeg ship native .so per ABI
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)
    implementation(libs.coroutines)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)

    implementation(libs.work.runtime)
    implementation(libs.datastore.prefs)
    implementation(libs.coil)

    implementation(libs.ktor.client)
    implementation(libs.ktor.json)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.logging)
    implementation(libs.kotlinx.serialization)

    // Downloader engine (Seal core: yt-dlp + ffmpeg native binaries)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    // Tagging engine (ID3v2 / MP4 / Vorbis). jaudiotagger must run off main thread.
    implementation(libs.jaudiotagger)
}
