package com.ddsk.app.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class JsAudioPlayer(private val fileName: String) : AudioPlayer {
    private val _currentTime = MutableStateFlow(0)

    // Minimal stub: no real audio playback yet.
    override val isPlaying: Boolean = false
    override val currentTime: StateFlow<Int> = _currentTime
    override val duration: Int = 0

    override fun play() {
        println("Audio playback not implemented for Web yet ($fileName)")
    }

    override fun pause() {}
    override fun stop() {
        _currentTime.value = 0
    }

    override fun release() {}
}

@Composable
actual fun rememberAudioPlayer(fileName: String): AudioPlayer {
    val player = remember(fileName) { JsAudioPlayer(fileName) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
