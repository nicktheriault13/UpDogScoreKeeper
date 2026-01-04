package com.ddsk.app.ui.screens.games

// The factory is the only part that needs to be platform-specific.
expect fun createZipInputStream(bytes: ByteArray): ZipStream

/** A simple Closeable interface for multiplatform projects. */
interface Closeable {
    fun close()
}

/** An interface for reading from a Zip stream. */
interface ZipStream : Closeable {
    fun nextEntryOrNull(): ZipEntryWrapper?
    fun readAllBytes(): ByteArray
    fun closeEntry()
}

/** A wrapper for a single entry in a Zip stream. */
interface ZipEntryWrapper {
    val name: String
}
