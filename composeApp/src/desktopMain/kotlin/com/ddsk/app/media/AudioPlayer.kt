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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer

@Suppress("unused")
class DesktopAudioPlayer(fileName: String) : AudioPlayer {
    private val assetPath: String = fileName
    private var mediaPlayer: MediaPlayer? = null

    // On desktop, Dispatchers.Main might not be installed. Use Default for polling.
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timeUpdateJob: Job? = null

    private val _currentTime = MutableStateFlow(0)
    override val currentTime: StateFlow<Int> = _currentTime.asStateFlow()

    override val duration: Int
        get() = (mediaPlayer
            ?.media
            ?.duration
            ?.toMillis()
            ?.takeIf { it.isFinite() }
            ?.toInt()) ?: 0

    override val isPlaying: Boolean
        get() = mediaPlayer?.status == MediaPlayer.Status.PLAYING

    init {
        try {
            ensureJavaFxInitialized()

            val loader = Thread.currentThread().contextClassLoader
            val candidates = listOf(assetPath, "assets/$assetPath")

            val resourceUrl = candidates.firstNotNullOfOrNull { path -> loader.getResource(path) }

            if (resourceUrl == null) {
                println("[DesktopAudioPlayer] Audio resource not found. Requested='$assetPath' Tried=$candidates")
                mediaPlayer = null
            } else {
                // JavaFX Media works best with a real file path.
                val tempFile = createTempFileForResource(resourceUrl.toString())
                tempFile.deleteOnExit()
                resourceUrl.openStream().use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

                runOnFxThread {
                    val media = Media(tempFile.toURI().toString())
                    mediaPlayer = MediaPlayer(media).apply {
                        setOnEndOfMedia {
                            // Match previous behavior: stop resets time to 0.
                            stop()
                        }
                        setOnError {
                            println("[DesktopAudioPlayer] JavaFX MediaPlayer error for '$assetPath': ${error}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[DesktopAudioPlayer] Failed to initialize audio for '$assetPath': ${e::class.qualifiedName}: ${e.message}")
            println(stacktraceToString(e))
            mediaPlayer = null
        }
    }

    override fun play() {
        val player = mediaPlayer ?: return
        runOnFxThread {
            player.play()
        }
        startTimeUpdates()
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        runOnFxThread { player.pause() }
        timeUpdateJob?.cancel()
    }

    override fun stop() {
        val player = mediaPlayer ?: return
        runOnFxThread {
            player.stop()
        }
        timeUpdateJob?.cancel()
        _currentTime.value = 0
    }

    override fun release() {
        timeUpdateJob?.cancel()
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runOnFxThread { player.dispose() }
        }
    }

    private fun startTimeUpdates() {
        timeUpdateJob?.cancel()
        timeUpdateJob = scope.launch {
            while (isPlaying) {
                val ms = mediaPlayer
                    ?.currentTime
                    ?.toMillis()
                    ?.takeIf { it.isFinite() }
                    ?.toInt() ?: 0
                _currentTime.value = ms
                delay(200)
            }
        }
    }
}

private fun stacktraceToString(t: Throwable): String {
    val sw = StringWriter()
    t.printStackTrace(PrintWriter(sw))
    return sw.toString()
}

private val javaFxInitialized = AtomicBoolean(false)

private fun ensureJavaFxInitialized() {
    if (javaFxInitialized.compareAndSet(false, true)) {
        // Initializes JavaFX runtime (required before using Media/MediaPlayer).
        JFXPanel()
    }
}

private fun runOnFxThread(block: () -> Unit) {
    if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}

private fun createTempFileForResource(urlString: String): File {
    val ext = when {
        urlString.endsWith(".mp3", ignoreCase = true) -> ".mp3"
        urlString.endsWith(".wav", ignoreCase = true) -> ".wav"
        else -> ".tmp"
    }
    return File.createTempFile("updog_audio_", ext)
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
