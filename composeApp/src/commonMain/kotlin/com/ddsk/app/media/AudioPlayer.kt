package com.ddsk.app.media

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * A common interface for playing audio.
 */
interface AudioPlayer {
    val isPlaying: Boolean
    val currentTime: StateFlow<Int> // Current time in milliseconds
    val duration: Int // Total duration in milliseconds

    fun play()
    fun pause()
    fun stop()
    fun release()
}

/**
 * A composable function that remembers an [AudioPlayer] instance
 * for the given [fileName]. The player is automatically released when
 * the composable leaves the composition.
 *
 * @param fileName The name of the audio file in the assets folder.
 */
@Composable
expect fun rememberAudioPlayer(fileName: String): AudioPlayer
