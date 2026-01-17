package com.ddsk.app.ui.screens.games

actual fun createZipInputStream(bytes: ByteArray): ZipStream {
    // Not implemented for JS yet.
    return object : ZipStream {
        override fun nextEntryOrNull(): ZipEntryWrapper? = null
        override fun readAllBytes(): ByteArray = ByteArray(0)
        override fun closeEntry() {}
        override fun close() {}
    }
}
