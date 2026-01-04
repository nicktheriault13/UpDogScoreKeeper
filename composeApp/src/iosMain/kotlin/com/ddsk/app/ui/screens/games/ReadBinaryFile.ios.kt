package com.ddsk.app.ui.screens.games

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.posix.memcpy

actual fun readBinaryFile(path: String): ByteArray? = runCatching {
    val manager = NSFileManager.defaultManager()
    val url = NSURL.fileURLWithPath(path)
    if (!manager.fileExistsAtPath(path)) return@runCatching null
    val data = NSData.create(contentsOfURL = url) ?: return@runCatching null
    val bytes = ByteArray(data.length.toInt())
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    bytes
}.getOrNull()

