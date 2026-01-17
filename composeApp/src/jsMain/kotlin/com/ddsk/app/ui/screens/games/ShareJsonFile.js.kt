package com.ddsk.app.ui.screens.games

actual suspend fun saveJsonFileWithPicker(filename: String, content: String) {
    // Minimal stub: could be implemented by creating a Blob and triggering a download.
    println("saveJsonFileWithPicker not implemented for Web yet: $filename (${content.length} chars)")
}

@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
actual suspend fun shareJsonFile(filename: String, content: String) {
    saveJsonFileWithPicker(filename, content)
}
