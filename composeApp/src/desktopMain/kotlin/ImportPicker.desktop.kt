/*
package com.ddsk.app.ui.screens.games

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun pickImportFile(): ImportResult = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("CSV or Excel", "csv", "xlsx", "xls", "xlsm")
        isAcceptAllFileFilterUsed = true
    }
    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile ?: return@withContext ImportResult.None
        val bytes = runCatching { file.readBytes() }.getOrElse { return@withContext ImportResult.None }
        when (file.extension.lowercase()) {
            "xlsx", "xlsm", "xls" -> ImportResult.Xlsx(bytes)
            else -> ImportResult.Csv(bytes.decodeToString())
        }
    } else {
        ImportResult.None
    }
}
*/
