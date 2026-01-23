package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable

// Consolidated from FileUtils.kt and GameImporter.kt
data class ImportedParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val jumpHeight: String = "",
    val heightDivision: String = "",
    val clubDivision: String = ""
)

@kotlinx.serialization.Serializable
data class FourWayPlayExportParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val zone1Catches: Int,
    val zone2Catches: Int,
    val zone3Catches: Int,
    val zone4Catches: Int,
    /** 1 or 0 */
    val sweetSpot: Int,
    /** 1 or 0 */
    val allRollers: Int,
    val heightDivision: String,
    val misses: Int
)

@kotlinx.serialization.Serializable
data class FunKeyExportParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val jump3Sum: Int,
    val jump2Sum: Int,
    val jump1TunnelSum: Int,
    val onePointClicks: Int,
    val twoPointClicks: Int,
    val threePointClicks: Int,
    val fourPointClicks: Int,
    val sweetSpot: String, // "Y" or "N"
    val allRollers: String // "Y" or "N"
)

// Remove duplicate SevenUpParticipant data class (defined in SevenUpScreenModel).
// SevenUp XLSM export uses the SevenUpParticipant type from SevenUpScreenModel.

sealed class ImportResult {
    data class Csv(val contents: String) : ImportResult()
    data class Xlsx(val bytes: ByteArray) : ImportResult()
    // Added during consolidation to fix FileUtils.android.kt references
    data object Cancelled : ImportResult()
    data class Error(val message: String) : ImportResult()
}

// Added for Export
data class ExportResult(val fileName: String, val data: ByteArray)

interface FileExporter {
    fun save(fileName: String, data: ByteArray)
}

@Composable
expect fun rememberFileExporter(): FileExporter

interface FilePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher

// Asset loader for reading bundled templates/docs across platforms
interface AssetLoader {
    /**
     * @param path relative path under the shared assets folder, e.g. "templates/foo.xlsx"
     */
    fun load(path: String): ByteArray?
}

@Composable
expect fun rememberAssetLoader(): AssetLoader

// These expected functions might be implemented differently on each platform
expect fun parseXlsx(bytes: ByteArray): List<ImportedParticipant>
expect fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateGreedyXlsx(participants: List<GreedyParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateFourWayPlayXlsx(participants: List<FourWayPlayExportParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateFireballXlsx(participants: List<FireballParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateTimeWarpXlsx(participants: List<TimeWarpParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateThrowNGoXlsx(participants: List<ThrowNGoParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateSevenUpXlsm(participants: List<SevenUpParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateFunKeyXlsm(participants: List<FunKeyExportParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateFrizgilityXlsx(participants: List<FrizgilityParticipantWithResults>, templateBytes: ByteArray): ByteArray
// Spaced Out XLSX export
expect fun generateSpacedOutXlsx(participants: List<SpacedOutExportParticipant>, templateBytes: ByteArray): ByteArray

// From GameImporter.kt
expect fun parseXlsxRows(bytes: ByteArray): List<List<String>>

fun parseCsv(content: String): List<ImportedParticipant> {
    val rows = parseCsvRows(content)
    return extractParticipantsFromRows(rows)
}

fun parseCsvRows(csv: String): List<List<String>> = csv.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        line.split(',').map { it.trim() }
    }

fun extractParticipantsFromRows(rows: List<List<String>>): List<ImportedParticipant> {
    val normalizedRows = rows.removeLeadingOrderColumn()
    val extractor = ColumnExtractor.from(normalizedRows) ?: ColumnExtractor.default(normalizedRows)
    return extractor.extract(normalizedRows)
}

private fun List<List<String>>.removeLeadingOrderColumn(): List<List<String>> {
    if (isEmpty()) return this
    val headerIndex = indexOfFirst { row -> row.any { it.isNotBlank() } }.takeIf { it >= 0 } ?: return this
    val headerRow = getOrElse(headerIndex) { emptyList() }
    val normalizedHeader = headerRow.map { it.trim().lowercase() }
    val orderIndex = normalizedHeader.indexOf("order").takeIf { it >= 0 } ?: return this
    val handlerExists = normalizedHeader.contains("handler")
    if (!handlerExists) return this
    return map { row ->
        if (row.isEmpty() || orderIndex >= row.size) row
        else row.filterIndexed { idx, _ -> idx != orderIndex }
    }
}

private data class ColumnExtractor(
    val handlerIdx: Int,
    val dogIdx: Int,
    val utnIdx: Int,
    val jumpHeightIdx: Int,
    val heightDivisionIdx: Int,
    val clubDivisionIdx: Int
) {
    fun extract(rows: List<List<String>>): List<ImportedParticipant> {
        // Try to locate the header row (preferred) using expected column names.
        val headerIndex = rows.indexOfFirst { row ->
            val r = row.map { it.trim().lowercase() }
            r.contains("handler") || r.contains("dog")
        }

        // Fallback: some exported templates may not preserve the header as plain strings
        // (e.g., empty leading rows, merged cells, or odd formatting on Android/POI).
        // In that case, treat the first "data-looking" row as the start of data.
        val dataRows = when {
            headerIndex >= 0 -> rows.drop(headerIndex + 1)
            else -> rows.dropWhile { row ->
                // Skip blank-ish rows; stop at the first row that looks like participant data
                // (at least 2 non-empty cells).
                row.count { it.trim().isNotEmpty() } < 2
            }
        }

        return dataRows.mapNotNull { row ->
            val handler = row.getOrNull(handlerIdx)?.trim().orEmpty()
            val dog = row.getOrNull(dogIdx)?.trim().orEmpty()
            val utn = row.getOrNull(utnIdx)?.trim().orEmpty()
            val jumpHeight = row.getOrNull(jumpHeightIdx)?.trim().orEmpty()
            val heightDivision = row.getOrNull(heightDivisionIdx)?.trim().orEmpty()
            val clubDivision = row.getOrNull(clubDivisionIdx)?.trim().orEmpty()

            if (handler.isNotBlank() || dog.isNotBlank()) {
                ImportedParticipant(handler, dog, utn, jumpHeight, heightDivision, clubDivision)
            } else null
        }
    }

    companion object {
        fun default(rows: List<List<String>>): ColumnExtractor {
            return ColumnExtractor(0, 1, 2, 3, 4, 5)
        }

        fun from(rows: List<List<String>>): ColumnExtractor? {
            val headerRow = rows.firstOrNull { row ->
                val r = row.map { it.trim().lowercase() }
                r.contains("handler") || r.contains("dog")
            } ?: return null

            val map = headerRow.mapIndexed { index, s -> s.trim().lowercase() to index }.toMap()

            return ColumnExtractor(
                handlerIdx = map["handler"] ?: 0,
                dogIdx = map["dog"] ?: 1,
                utnIdx = map["utn"] ?: 2,
                jumpHeightIdx = map["jump height"] ?: map["jumpheight"] ?: -1,
                heightDivisionIdx = map["height division"] ?: map["heightdivision"] ?: -1,
                clubDivisionIdx = map["club division"] ?: map["clubdivision"] ?: -1
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class SpacedOutExportParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val zonesCaught: Int,
    val spacedOut: Int,
    val misses: Int,
    val ob: Int,
    /** 1 for sweet spot yes, 0 for no (keeps export stable across platforms) */
    val sweetSpot: Int,
    /** 1 for all rollers yes, 0 for no */
    val allRollers: Int,
    val heightDivision: String = ""
)
