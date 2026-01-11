package com.ddsk.app.ui.screens.games

/**
 * iOS implementation: currently a no-op.
 *
 * (We can later wire this to UIDocumentInteractionController / UIActivityViewController)
 */
actual suspend fun saveJsonFileWithPicker(filename: String, content: String) {
    // no-op
}

@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
actual suspend fun shareJsonFile(filename: String, content: String) {
    saveJsonFileWithPicker(filename, content)
}
