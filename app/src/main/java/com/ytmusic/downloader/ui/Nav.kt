package com.ytmusic.downloader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AppNav(deepLink: String = "") {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Download, null) }, label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") }
                )
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> SearchScreen(initialQuery = deepLink.extractVideoIdOrQuery())
                1 -> DownloadsScreen()
                else -> LibraryScreen()
            }
        }
    }
}

private fun String.extractVideoIdOrQuery(): String {
    val v = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(this)?.groupValues?.get(1)
    val list = Regex("[?&]list=([A-Za-z0-9_-]+)").find(this)?.groupValues?.get(1)
    return v ?: list ?: ""
}
