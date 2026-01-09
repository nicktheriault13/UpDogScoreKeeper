package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable
import com.ddsk.app.ui.screens.games.GreedyScreenModel

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    return object : FilePickerLauncher {
        override fun launch() {
            // Not implemented for iOS in this task
            onResult(ImportResult.Error("Not supported on iOS yet"))
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> {
    return emptyList() // Not supported
}

actual fun generateFarOutXlsx(participants: List<com.ddsk.app.ui.screens.games.FarOutParticipant>, templateBytes: ByteArray): ByteArray {
    return ByteArray(0)
}

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    return emptyList()
}

@Composable
actual fun rememberFileExporter(): FileExporter {
    return object : FileExporter {
        override fun save(fileName: String, data: ByteArray) {
            println("Export not supported on iOS")
        }
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader {
    return object : AssetLoader {
        override fun load(path: String): ByteArray? {
            return null
        }
    }
}

actual fun generateGreedyXlsx(participants: List<GreedyScreenModel.GreedyParticipant>, templateBytes: ByteArray): ByteArray {
    // iOS implementation pending
    return ByteArray(0)
}
