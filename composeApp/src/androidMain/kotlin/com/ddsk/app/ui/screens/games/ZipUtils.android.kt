package com.ddsk.app.ui.screens.games

import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

actual fun createZipInputStream(bytes: ByteArray): ZipStream {
    return AndroidZipStream(ZipInputStream(ByteArrayInputStream(bytes)))
}

private class AndroidZipStream(private val zipInputStream: ZipInputStream) : ZipStream {
    override fun nextEntryOrNull(): ZipEntryWrapper? {
        return zipInputStream.nextEntry?.let { AndroidZipEntry(it) }
    }

    override fun readAllBytes(): ByteArray {
        return zipInputStream.readBytes()
    }

    override fun closeEntry() {
        zipInputStream.closeEntry()
    }

    override fun close() {
        zipInputStream.close()
    }
}

private class AndroidZipEntry(private val entry: ZipEntry) : ZipEntryWrapper {
    override val name: String
        get() = entry.name
}
