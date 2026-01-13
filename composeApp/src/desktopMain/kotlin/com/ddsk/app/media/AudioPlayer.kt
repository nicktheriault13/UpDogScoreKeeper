package com.ddsk.app.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

@Suppress("unused")
class DesktopAudioPlayer(fileName: String) : AudioPlayer {
    private val assetPath: String = fileName
    private var clip: Clip? = null

    // On desktop, Dispatchers.Main might not be installed. Use Default for polling.
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timeUpdateJob: Job? = null

    private val _currentTime = MutableStateFlow(0)
    override val currentTime: StateFlow<Int> = _currentTime.asStateFlow()

    override val duration: Int
        get() = (clip?.microsecondLength?.div(1000L))?.toInt() ?: 0

    override val isPlaying: Boolean
        get() = clip?.isRunning ?: false

    init {
        try {
            val resource = Thread.currentThread().contextClassLoader.getResource(assetPath)
            if (resource != null) {
                // Ensure the stream is closed to avoid file handle leaks.
                val audioInputStream = AudioSystem.getAudioInputStream(resource)
                try {
                    clip = AudioSystem.getClip().apply { open(audioInputStream) }
                } finally {
                    (audioInputStream as? Closeable)?.close()
                }
            } else {
                // If the resource isn't bundled, keep clip null and gracefully no-op.
                clip = null
            }
        } catch (_: Exception) {
            // Never crash the UI because of missing/bad audio assets.
            clip = null
        }
    }

    override fun play() {
        clip?.let {
            if (!it.isRunning) {
                it.start()
                startTimeUpdates()
            }
        }
    }

    override fun pause() {
        clip?.stop()
        timeUpdateJob?.cancel()
    }

    override fun stop() {
        clip?.stop()
        clip?.framePosition = 0
        timeUpdateJob?.cancel()
        _currentTime.value = 0
    }

    override fun release() {
        timeUpdateJob?.cancel()
        clip?.close()
        clip = null
    }

    private fun startTimeUpdates() {
        timeUpdateJob?.cancel()
        timeUpdateJob = scope.launch {
            while (isPlaying) {
                _currentTime.value = (clip?.microsecondPosition?.div(1000L))?.toInt() ?: 0
                delay(200)
            }
        }
    }
}

@Composable
actual fun rememberAudioPlayer(fileName: String): AudioPlayer {
    val player = remember(fileName) {
        DesktopAudioPlayer(fileName)
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
