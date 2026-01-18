package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFileExporter(): FileExporter = object : FileExporter {
    override fun save(fileName: String, data: ByteArray) {}
}

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher = object : FilePickerLauncher {
    override fun launch() {
        onResult(ImportResult.Cancelled)
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader = object : AssetLoader {
    override fun load(path: String): ByteArray? = null
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> = emptyList()
actual fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateGreedyXlsx(participants: List<GreedyParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateFourWayPlayXlsx(participants: List<FourWayPlayExportParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateFireballXlsx(participants: List<FireballParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateTimeWarpXlsx(participants: List<TimeWarpParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateThrowNGoXlsx(participants: List<ThrowNGoParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateSevenUpXlsm(participants: List<SevenUpParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun generateSpacedOutXlsx(participants: List<SpacedOutExportParticipant>, templateBytes: ByteArray): ByteArray = ByteArray(0)
actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> = emptyList()
