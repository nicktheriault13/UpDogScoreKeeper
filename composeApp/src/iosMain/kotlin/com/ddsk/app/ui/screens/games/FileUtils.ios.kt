package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    return object : FilePickerLauncher {
        override fun launch() {
            onResult(ImportResult.Error("Not supported on iOS yet"))
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> = emptyList()

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> = emptyList()

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
    // iOS asset bundling/loading not wired-up for this project yet.
    return object : AssetLoader {
        override fun load(path: String): ByteArray? = null
    }
}

actual fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

// iOS currently returns an empty workbook; keep signature stable after GreedyParticipant changes.
actual fun generateGreedyXlsx(participants: List<GreedyParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

actual fun generateFourWayPlayXlsx(participants: List<FourWayPlayExportParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

actual fun generateFireballXlsx(participants: List<FireballParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

actual fun generateTimeWarpXlsx(participants: List<TimeWarpParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

actual fun generateThrowNGoXlsx(participants: List<ThrowNGoParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)

actual fun generateSevenUpXlsm(participants: List<SevenUpParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
