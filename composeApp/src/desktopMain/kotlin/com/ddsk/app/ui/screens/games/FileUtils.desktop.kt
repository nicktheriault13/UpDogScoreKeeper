package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable
import com.ddsk.app.ui.screens.games.GreedyScreenModel

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    return object : FilePickerLauncher {
        override fun launch() {
            // Not implemented for Desktop in this task without library
            onResult(ImportResult.Error("Not supported on Desktop yet"))
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> {
    return emptyList() // Not supported without POI on desktop
}

actual fun generateFarOutXlsx(participants: List<com.ddsk.app.ui.screens.games.FarOutParticipant>, templateBytes: ByteArray): ByteArray {
    return ByteArray(0) // Not supported on desktop yet without POI
}

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    return emptyList()
}

@Composable
actual fun rememberFileExporter(): FileExporter {
    return object : FileExporter {
        override fun save(fileName: String, data: ByteArray) {
            try {
                val file = java.io.File(fileName)
                file.writeBytes(data)
                println("Saved export to ${file.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader {
    return object : AssetLoader {
        override fun load(path: String): ByteArray? {
            return try {
                val file = java.io.File(path)
                if (file.exists()) file.readBytes() else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

actual fun generateGreedyXlsx(participants: List<GreedyScreenModel.GreedyParticipant>, templateBytes: ByteArray): ByteArray {
    return ByteArray(0)
}
