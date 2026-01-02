package com.ddsk.app.media

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AndroidAudioPlayer(private val context: Context, private val fileName: String) : AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timeUpdateJob: Job? = null

    private val _currentTime = MutableStateFlow(0)
    override val currentTime: StateFlow<Int> = _currentTime.asStateFlow()

    override val duration: Int
        get() = mediaPlayer?.duration ?: 0

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying ?: false

    init {
        // Eagerly initialize to get duration, but don't play.
        try {
            val inputStream = this::class.java.classLoader.getResourceAsStream(fileName)
                ?: throw IOException("Audio file '$fileName' not found.")

            val tempFile = File.createTempFile("audio", ".tmp", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { output -> inputStream.use { it.copyTo(output) } }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener { stop() }
            }
        } catch (e: Exception) {
            mediaPlayer = null
        }
    }

    override fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startTimeUpdates()
            }
        }
    }

    override fun pause() {
        mediaPlayer?.pause()
        timeUpdateJob?.cancel()
    }

    override fun stop() {
        mediaPlayer?.apply {
            stop()
            prepare()
        }
        timeUpdateJob?.cancel()
        _currentTime.value = 0
    }

    override fun release() {
        timeUpdateJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startTimeUpdates() {
        timeUpdateJob?.cancel()
        timeUpdateJob = scope.launch {
            while (isPlaying) {
                _currentTime.value = mediaPlayer?.currentPosition ?: 0
                delay(200) // Update 5 times per second
            }
        }
    }
}

@Composable
actual fun rememberAudioPlayer(fileName: String): AudioPlayer {
    val context = LocalContext.current
    val player = remember(fileName) {
        AndroidAudioPlayer(context, fileName)
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    return player
}
