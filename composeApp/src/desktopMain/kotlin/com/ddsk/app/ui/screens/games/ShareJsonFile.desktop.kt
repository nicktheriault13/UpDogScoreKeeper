package com.ddsk.app.ui.screens.games

import java.io.File

actual suspend fun saveJsonFileWithPicker(filename: String, content: String) {
    // Desktop: no picker currently wired; default to user home.
    val file = File(System.getProperty("user.home"), filename)
    file.writeText(content)
}

@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
actual suspend fun shareJsonFile(filename: String, content: String) {
    saveJsonFileWithPicker(filename, content)
}
