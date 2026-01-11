package com.ddsk.app.ui.screens.games

/**
 * Platform-specific JSON save/export helper.
 *
 * Implementations should prompt the user to choose a save location when possible.
 */
expect suspend fun saveJsonFileWithPicker(filename: String, content: String)

// Backwards-compatible alias for older call sites.
@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
expect suspend fun shareJsonFile(filename: String, content: String)
