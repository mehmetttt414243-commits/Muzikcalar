package com.example.muzikcalar

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get(); setUi() }, MoreExecutors.directExecutor())
    }

    private fun setUi() {
        val c = controller ?: return
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { PlayerScreen(c) } }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@Composable
fun PlayerScreen(player: Player) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString() ?: "") }
    var playlist by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableIntStateOf(player.currentMediaItemIndex) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                title = item?.mediaMetadata?.title?.toString() ?: ""
                current = player.currentMediaItemIndex
            }
        }
        player.addListener(l)
        playlist = (0 until player.mediaItemCount).map {
            player.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "Parça ${it + 1}"
        }
        onDispose { player.removeListener(l) }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val items = uris.mapIndexed { i, uri ->
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Parça ${i + 1}"
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                .build()
        }
        player.setMediaItems(items)
        player.prepare()
        player.play()
        playlist = items.map { it.mediaMetadata.title.toString() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).systemBarsPadding()) {
        Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LibraryMusic, null)
            Spacer(Modifier.width(8.dp))
            Text("Müzik seç")
        }

        LazyColumn(Modifier.weight(1f).padding(vertical = 12.dp)) {
            itemsIndexed(playlist) { i, name ->
                Text(
                    text = name,
                    color = if (i == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { player.seekTo(i, 0); player.play() }
                        .padding(vertical = 12.dp)
                )
            }
        }

        Text(title.ifEmpty { "Parça seçilmedi" }, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.seekToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, "Önceki", Modifier.size(40.dp))
            }
            FilledIconButton(
                onClick = { if (isPlaying) player.pause() else player.play() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Oynat/Duraklat", Modifier.size(36.dp))
            }
            IconButton(onClick = { player.seekToNext() }) {
                Icon(Icons.Default.SkipNext, "Sonraki", Modifier.size(40.dp))
            }
        }
    }
}
