package com.ddsk.app.ui.screens.games

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.math.pow

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    val sharedStrings = mutableListOf<String>()
    val sheets = mutableListOf<List<List<String>>>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            when {
                entry.name == "xl/sharedStrings.xml" ->
                    sharedStrings.addAll(readSharedStrings(zip))
                entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) ->
                    sheets += readSheet(zip, sharedStrings)
            }
            zip.closeEntry()
        }
    }
    return sheets.firstOrNull() ?: emptyList()
}

private fun readSharedStrings(stream: ZipInputStream): List<String> {
    val xml = stream.readBytes().decodeToString()
    val entryRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    val textRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    return entryRegex.findAll(xml).map { entryMatch ->
        val concat = textRegex.findAll(entryMatch.value)
            .joinToString(separator = "") { it.groupValues[1] }
            .unescapeXmlEntities()
        concat
    }.toList()
}

private fun readSheet(stream: ZipInputStream, sharedStrings: List<String>): List<List<String>> {
    val xml = stream.readBytes().decodeToString()
    val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    val cellRegex = Regex("<c[^>]*?r=\"([A-Z]+)(\\d+)\"[^>]*?(t=\"([a-z]+)\")?[^>]*?>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
    val valueRegex = Regex("<v>(.*?)</v>")
    val rows = mutableListOf<List<String>>()
    rowRegex.findAll(xml).forEach { rowMatch ->
        val cells = mutableMapOf<Int, String>()
        cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
            val columnLabel = cellMatch.groupValues[1]
            val cellIndex = columnLabel.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
            val cellType = cellMatch.groupValues[4]
            val cellBody = cellMatch.groupValues[5]
            val value = valueRegex.find(cellBody)?.groupValues?.get(1)
            val parsed = when (cellType) {
                "s" -> value?.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                else -> value ?: ""
            }
            cells[cellIndex] = parsed
        }
        if (cells.isNotEmpty()) {
            val lastIndex = cells.keys.maxOrNull() ?: 0
            val rowData = (0..lastIndex).map { cells[it] ?: "" }
            rows += rowData
        }
    }
    return rows
}

private fun String.unescapeXmlEntities(): String =
    replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

