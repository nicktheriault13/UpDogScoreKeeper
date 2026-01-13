package com.ddsk.app.ui.screens.games

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun saveJsonFileWithPicker(filename: String, content: String) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save JSON Export"
        fileFilter = FileNameExtensionFilter("JSON Files", "json")
        selectedFile = File(filename)
        isAcceptAllFileFilterUsed = true
    }

    val result = chooser.showSaveDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val chosenFile = chooser.selectedFile?.let {
            // Ensure .json extension
            if (it.name.lowercase().endsWith(".json")) it else File(it.parentFile, "${it.name}.json")
        }
        if (chosenFile != null) {
            withContext(Dispatchers.IO) {
                chosenFile.writeText(content)
            }
        }
    }
}

@Deprecated("Use saveJsonFileWithPicker", ReplaceWith("saveJsonFileWithPicker(filename, content)"))
actual suspend fun shareJsonFile(filename: String, content: String) {
    saveJsonFileWithPicker(filename, content)
}
