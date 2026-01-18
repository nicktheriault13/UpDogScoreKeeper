package com.ddsk.app.logging

import androidx.compose.runtime.Composable

actual class GameLogger {
    actual fun log(action: String) {
        // Stub: no-op on wasm
    }

    actual fun getLogContents(): String {
        return ""
    }
}

@Composable
actual fun rememberGameLogger(gameName: String): GameLogger {
    return GameLogger()
}
