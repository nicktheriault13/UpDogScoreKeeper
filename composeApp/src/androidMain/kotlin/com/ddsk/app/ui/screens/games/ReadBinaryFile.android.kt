package com.ddsk.app.ui.screens.games

import java.io.File

actual fun readBinaryFile(path: String): ByteArray? = runCatching {
    val file = File(path)
    if (file.exists() && file.isFile && file.canRead()) file.readBytes() else null
}.getOrNull()

