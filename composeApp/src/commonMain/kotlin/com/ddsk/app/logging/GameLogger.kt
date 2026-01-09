package com.ddsk.app.logging

import androidx.compose.runtime.Composable

expect class GameLogger {
    fun log(action: String)
    fun getLogContents(): String
}

@Composable
expect fun rememberGameLogger(gameName: String): GameLogger
