package com.ddsk.app.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

actual class GameLogger(private val file: File) {

    init {
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    actual fun log(action: String) {
        file.appendText("[${System.currentTimeMillis()}] $action\n")
    }

    actual fun getLogContents(): String {
        return if (file.exists()) file.readText() else ""
    }
}

@Composable
actual fun rememberGameLogger(gameName: String): GameLogger {
    val file = remember(gameName) {
        File("$gameName.log")
    }
    return remember(file) { GameLogger(file) }
}
