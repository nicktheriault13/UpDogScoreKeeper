package com.ddsk.app.ui.screens.games

actual fun readBinaryFile(path: String): ByteArray? {
    // Not supported in web build without fetch()/bundled resources.
    return null
}
