package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable

/**
 * Web implementations are currently minimal.
 * Import/export flows can be added later using <input type=file> and the File System Access API.
 */
@Composable
actual fun rememberFileExporter(): FileExporter {
    return object : FileExporter {
        override fun save(fileName: String, data: ByteArray) {
            println("Export not supported on Web yet: $fileName (${data.size} bytes)")
        }
    }
}

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    return object : FilePickerLauncher {
        override fun launch() {
            onResult(ImportResult.Error("Import not supported on Web yet"))
        }
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader {
    return object : AssetLoader {
        override fun load(path: String): ByteArray? {
            // Could be implemented via fetch() if you serve under /assets/...
            return null
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> = emptyList()
actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> = emptyList()

actual fun generateFarOutXlsx(
    participants: List<FarOutParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateGreedyXlsx(
    participants: List<GreedyParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateFourWayPlayXlsx(
    participants: List<FourWayPlayExportParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateFireballXlsx(
    participants: List<FireballParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateTimeWarpXlsx(
    participants: List<TimeWarpParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateThrowNGoXlsx(
    participants: List<ThrowNGoParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateSevenUpXlsm(
    participants: List<SevenUpParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)

actual fun generateSpacedOutXlsx(
    participants: List<SpacedOutExportParticipant>,
    templateBytes: ByteArray
): ByteArray = ByteArray(0)
