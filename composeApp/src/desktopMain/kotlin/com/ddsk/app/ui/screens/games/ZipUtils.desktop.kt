package com.ddsk.app.ui.screens.games

import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

actual fun createZipInputStream(bytes: ByteArray): ZipStream {
    return DesktopZipStream(ZipInputStream(ByteArrayInputStream(bytes)))
}

private class DesktopZipStream(private val stream: ZipInputStream) : ZipStream {
    override fun nextEntryOrNull(): ZipEntryWrapper? {
        return stream.nextEntry?.let { DesktopZipEntry(it) }
    }

    override fun readAllBytes(): ByteArray = stream.readBytes()

    override fun closeEntry() {
        stream.closeEntry()
    }

    override fun close() {
        stream.close()
    }
}

private class DesktopZipEntry(private val entry: ZipEntry) : ZipEntryWrapper {
    override val name: String get() = entry.name
}

