package com.ytmusic.downloader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ytmusic.downloader.ui.theme.YTMDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle shared YouTube Music links: .../watch?v=xxx or playlist?list=xxx
        val sharedText = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)
            .orEmpty() + " " + (intent?.dataString.orEmpty())
        setContent {
            YTMDownloaderTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav(deepLink = sharedText)
                }
            }
        }
    }
}
