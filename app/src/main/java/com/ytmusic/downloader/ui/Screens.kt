package com.ytmusic.downloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ytmusic.downloader.downloader.AudioFormat
import com.ytmusic.downloader.innertube.InnerTubeClient
import com.ytmusic.downloader.innertube.YTSong
import com.ytmusic.downloader.worker.TrackDownloadWorker
import kotlinx.coroutines.launch

class SearchVm : ViewModel() {
    private val tube = InnerTubeClient()
    var query = mutableStateOf("")
    var results = mutableStateOf<List<YTSong>>(emptyList())
    var loading = mutableStateOf(false)
    var error = mutableStateOf<String?>(null)

    fun search(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching { tube.search(q) }
                .onSuccess { results.value = it }
                .onFailure { error.value = it.message }
            loading.value = false
        }
    }
}

@Composable
fun SearchScreen(initialQuery: String = "", vm: SearchVm = viewModel()) {
    val ctx = LocalContext.current
    var format by remember { mutableStateOf(AudioFormat.MP3) }
    var menu by remember { mutableStateOf(false) }
    val results by vm.results
    val q by vm.query
    val loading by vm.loading
    val error by vm.error

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it },
            label = { Text("Search YouTube Music") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { vm.search(q.ifBlank { initialQuery }) }) {
                Text(if (loading) "Searching…" else "Search")
            }
            Button(onClick = { menu = true }, modifier = Modifier.padding(start = 8.dp)) {
                Text(format.name)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AudioFormat.entries.forEach {
                    DropdownMenuItem(text = { Text(it.name) }, onClick = { format = it; menu = false })
                }
            }
        }
        if (initialQuery.isNotBlank()) {
            Text(
                "Link detected: $initialQuery — tap Search to resolve.",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        error?.let { Text("Error: $it", modifier = Modifier.padding(top = 8.dp)) }
        LazyColumn {
            items(results) { song ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clickable {
                            TrackDownloadWorker.enqueue(ctx, song.videoId, format, song.title)
                        }
                ) {
                    AsyncImage(
                        model = song.artworkUrl, contentDescription = null,
                        modifier = Modifier.size(56.dp).padding(end = 12.dp)
                    )
                    Column {
                        Text(song.title)
                        Text("${song.artist} • ${song.album}")
                        Text("Tap to download as ${format.name} (cover + synced lyrics embedded)")
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Downloads run in the background (WorkManager).")
        Text("Files land in the app Music folder, tagged + indexed via MediaScanner.")
        Text("Each file: title, artist, album, hi-res cover, synced LRC lyrics.")
    }
}

@Composable
fun LibraryScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Library shows downloaded files with embedded artwork + lyrics.")
        Text("Streaming also resolves only from YouTube Music (InnerTube).")
    }
}
