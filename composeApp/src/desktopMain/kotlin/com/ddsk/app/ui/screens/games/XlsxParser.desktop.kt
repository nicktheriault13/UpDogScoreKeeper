package com.ddsk.app.ui.screens.games

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    var sharedStrings = listOf<String>()
    val sheetXmls = mutableMapOf<String, String>()

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            val name = entry.name
            if (name == "xl/sharedStrings.xml") {
                val xml = zip.readBytes().decodeToString()
                sharedStrings = parseSharedStrings(xml)
            } else if (name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))) {
                sheetXmls[name] = zip.readBytes().decodeToString()
            }
            zip.closeEntry()
        }
    }

    val firstSheetXml = sheetXmls.entries.sortedBy { it.key }.firstOrNull()?.value ?: return emptyList()
    return parseSheet(firstSheetXml, sharedStrings)
}

private fun parseSharedStrings(xml: String): List<String> {
    val entryRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    val textRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    return entryRegex.findAll(xml).map { entryMatch ->
        textRegex.findAll(entryMatch.value)
            .joinToString(separator = "") { it.groupValues[1] }
            .unescapeXmlEntities()
    }.toList()
}

private fun parseSheet(xml: String, sharedStrings: List<String>): List<List<String>> {
    val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
    val cellRegex = Regex("<c\\s+([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
    val rAttrRegex = Regex("r=\"([A-Z]+)(\\d+)\"")
    val tAttrRegex = Regex("t=\"([a-z]+)\"")
    val vTagRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    val tTagRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

    val rows = mutableListOf<List<String>>()

    rowRegex.findAll(xml).forEach { rowMatch ->
        val rowBody = rowMatch.groupValues[1]
        val cells = mutableMapOf<Int, String>()

        cellRegex.findAll(rowBody).forEach { cellMatch ->
            val attrs = cellMatch.groupValues[1]
            val body = cellMatch.groupValues[2]

            val rMatch = rAttrRegex.find(attrs)
            if (rMatch != null) {
                val colStr = rMatch.groupValues[1]
                val cellIndex = colStr.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

                val tMatch = tAttrRegex.find(attrs)
                val type = tMatch?.groupValues?.get(1) ?: ""

                var value = ""

                if (type == "s") {
                    val idxStr = vTagRegex.find(body)?.groupValues?.get(1)
                    val idx = idxStr?.toIntOrNull()
                    if (idx != null) {
                        value = sharedStrings.getOrElse(idx) { "" }
                    }
                } else if (type == "inlineStr") {
                    value = tTagRegex.find(body)?.groupValues?.get(1) ?: ""
                } else {
                    value = vTagRegex.find(body)?.groupValues?.get(1) ?: ""
                }
                cells[cellIndex] = value.unescapeXmlEntities()
            }
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
