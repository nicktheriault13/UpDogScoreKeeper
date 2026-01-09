package com.ddsk.app.ui.screens.games

import java.io.File

actual suspend fun shareJsonFile(filename: String, content: String) {
    val file = File(System.getProperty("user.home"), filename)
    file.writeText(content)
}
