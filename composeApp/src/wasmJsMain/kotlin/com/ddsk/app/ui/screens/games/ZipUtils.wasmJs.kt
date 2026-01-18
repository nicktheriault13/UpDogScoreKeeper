package com.ddsk.app.ui.screens.games

actual fun createZipInputStream(bytes: ByteArray): ZipStream {
    throw UnsupportedOperationException("Zip not supported on wasm")
}
