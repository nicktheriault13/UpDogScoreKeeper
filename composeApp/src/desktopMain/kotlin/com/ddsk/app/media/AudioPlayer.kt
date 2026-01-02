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
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class DesktopAudioPlayer(private val fileName: String) : AudioPlayer {
    private var clip: Clip? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timeUpdateJob: Job? = null

    private val _currentTime = MutableStateFlow(0)
    override val currentTime: StateFlow<Int> = _currentTime.asStateFlow()

    override val duration: Int
        get() = (clip?.microsecondLength?.div(1000L))?.toInt() ?: 0

    override val isPlaying: Boolean
        get() = clip?.isRunning ?: false

    init {
        try {
            val resource = Thread.currentThread().contextClassLoader.getResource(fileName)
            val audioInputStream = AudioSystem.getAudioInputStream(resource)
            clip = AudioSystem.getClip()
            clip?.open(audioInputStream)
        } catch (e: Exception) {
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
