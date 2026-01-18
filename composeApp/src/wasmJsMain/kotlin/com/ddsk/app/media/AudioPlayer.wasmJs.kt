package com.ddsk.app.media

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class WasmAudioPlayer(private val fileName: String) : AudioPlayer {
    private val _currentTime = MutableStateFlow(0)
    override val isPlaying: Boolean = false
    override val currentTime: StateFlow<Int> = _currentTime
    override val duration: Int = 0
    override fun play() {}
    override fun pause() {}
    override fun stop() { _currentTime.value = 0 }
    override fun release() {}
}

@Composable
actual fun rememberAudioPlayer(fileName: String): AudioPlayer {
    return WasmAudioPlayer(fileName)
}
