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

// These expected functions might be implemented differently on each platform
expect fun parseXlsx(bytes: ByteArray): List<ImportedParticipant>
expect fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray
expect fun generateGreedyXlsx(participants: List<GreedyScreenModel.GreedyParticipant>, templateBytes: ByteArray): ByteArray

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
        // Skip header - find first row that looks like data or just skip the header line
        val headerIndex = rows.indexOfFirst { row ->
            val r = row.map { it.trim().lowercase() }
            r.contains("handler") || r.contains("dog")
        }
        val dataRows = if (headerIndex >= 0) rows.drop(headerIndex + 1) else rows

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

interface AssetLoader {
    fun load(path: String): ByteArray?
}

@Composable
expect fun rememberAssetLoader(): AssetLoader
