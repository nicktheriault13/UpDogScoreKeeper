package com.ddsk.app.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class GameLogger(private val gameName: String) {
    private val lines = mutableListOf<String>()

    actual fun log(action: String) {
        val msg = "[$gameName] $action"
        lines += msg
        // Browser console
        println(msg)
    }

    actual fun getLogContents(): String = lines.joinToString("\n")
}

@Composable
actual fun rememberGameLogger(gameName: String): GameLogger = remember(gameName) { GameLogger(gameName) }
