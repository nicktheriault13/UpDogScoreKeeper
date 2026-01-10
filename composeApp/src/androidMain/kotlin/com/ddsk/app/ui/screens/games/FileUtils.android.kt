package com.ddsk.app.ui.screens.games

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
actual fun rememberFileExporter(): FileExporter {
    val context = LocalContext.current
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.ms-excel.sheet.macroEnabled.12"),
        onResult = { uri ->
            if (uri != null && pendingData != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(pendingData)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingData = null
                }
            }
        }
    )

    return object : FileExporter {
        override fun save(fileName: String, data: ByteArray) {
            pendingData = data
            launcher.launch(fileName)
        }
    }
}

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val result = contentResolver.openInputStream(uri)?.use { inputStream ->
                        // Simple mime type check or extension check
                        val type = contentResolver.getType(uri) ?: ""
                        if (type.contains("csv") || uri.toString().endsWith(".csv", ignoreCase = true)) {
                            val content = inputStream.bufferedReader().use { it.readText() }
                            ImportResult.Csv(content)
                        } else {
                            val bytes = inputStream.readBytes()
                            ImportResult.Xlsx(bytes)
                        }
                    } ?: ImportResult.Error("Could not read file")
                    onResult(result)
                } catch (e: Exception) {
                    onResult(ImportResult.Error("Error: ${e.message}"))
                }
            } else {
                onResult(ImportResult.Cancelled)
            }
        }
    )

    return object : FilePickerLauncher {
        override fun launch() {
            launcher.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> {
    val rows = parseXlsxRows(bytes)
    return extractParticipantsFromRows(rows)
}

actual fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    try {
        val inputStream = ByteArrayInputStream(bytes)
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        for (row in sheet) {
            val rowData = mutableListOf<String>()
            val lastCellNum = row.lastCellNum.toInt()
            for (i in 0 until lastCellNum) {
                val cell = row.getCell(i)
                val cellValue = if (cell == null) "" else {
                    when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> {
                            // Check if date or just number
                            val num = cell.numericCellValue
                            if (num % 1.0 == 0.0) String.format("%.0f", num) else num.toString()
                        }
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> ""
                    }
                }
                rowData.add(cellValue)
            }
            if (rowData.any { it.isNotBlank() }) {
                rows.add(rowData)
            }
        }
        workbook.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return rows
}

actual fun generateFarOutXlsx(participants: List<FarOutParticipant>, templateBytes: ByteArray): ByteArray {
    try {
        val inputStream = ByteArrayInputStream(templateBytes)
        val workbook = WorkbookFactory.create(inputStream)
        var worksheet = workbook.getSheet("Data Entry Mens")
        if (worksheet == null) {
             worksheet = workbook.getSheetAt(0)
        }

        val startRow = 3 // Row 4 (0-indexed 3)

        // Sort participants
        val sortedParticipants = participants.sortedWith(
            compareByDescending<FarOutParticipant> { it.score }
                .thenByDescending { p ->
                    val t1 = if (p.throw1Miss) 0.0 else p.throw1.toDoubleOrNull() ?: 0.0
                    val t2 = if (p.throw2Miss) 0.0 else p.throw2.toDoubleOrNull() ?: 0.0
                    val t3 = if (p.throw3Miss) 0.0 else p.throw3.toDoubleOrNull() ?: 0.0
                    val ss = if (p.sweetShotMiss) 0.0 else p.sweetShot.toDoubleOrNull() ?: 0.0
                    max(max(t1, t2), max(t3, ss))
                }
        )

        sortedParticipants.forEachIndexed { index, p ->
            val rowNum = startRow + index
            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column A (0): Level -> 1
            row.createCell(0).setCellValue(1.0)

            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)

            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)

            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)

            // Column E (4): throw1
            val t1 = if (p.throw1Miss) 0.0 else p.throw1.toDoubleOrNull() ?: 0.0
            row.createCell(4).setCellValue(t1)

            // Column F (5): throw2
            val t2 = if (p.throw2Miss) 0.0 else p.throw2.toDoubleOrNull() ?: 0.0
            row.createCell(5).setCellValue(t2)

            // Column G (6): throw3
            val t3 = if (p.throw3Miss) 0.0 else p.throw3.toDoubleOrNull() ?: 0.0
            row.createCell(6).setCellValue(t3)

            // Column J (9): Sweet Shot Declined? (N/Y)
            row.createCell(9).setCellValue(if (p.sweetShotDeclined) "N" else "Y")

            // Column K (10): Sweet Shot Value
            val ss = if (p.sweetShotMiss) 0.0 else p.sweetShot.toDoubleOrNull() ?: 0.0
            row.createCell(10).setCellValue(ss)

            // Column M (12): AllRollers (Y/N)
            row.createCell(12).setCellValue(if (p.allRollers) "Y" else "N")
        }

        val bos = ByteArrayOutputStream()
        workbook.write(bos)
        workbook.close()
        return bos.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        return ByteArray(0)
    }
}


actual fun generateGreedyXlsx(participants: List<GreedyScreenModel.GreedyParticipant>, templateBytes: ByteArray): ByteArray {
    try {
        val inputStream = ByteArrayInputStream(templateBytes)
        val workbook = WorkbookFactory.create(inputStream)
        var worksheet = workbook.getSheet("Data Entry Sheet")
        if (worksheet == null) {
             worksheet = workbook.getSheetAt(0)
        }

        val startRow = 3 // Row 4 (0-based is 3)

        // Sort participants
        val sortedParticipants = participants.sortedWith(
            compareByDescending<GreedyScreenModel.GreedyParticipant> { it.score }
                .thenByDescending { it.zone4Catches }
                .thenByDescending { it.zone3Catches }
                .thenByDescending { it.zone2Catches }
                .thenByDescending { it.zone1Catches }
                .thenBy { it.numberOfMisses }
        )

        sortedParticipants.forEachIndexed { index, p ->
            val rowNum = startRow + index
            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)

            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)

            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)

            // Column E (4): Zone 1 Catches
            row.createCell(4).setCellValue(p.zone1Catches.toDouble())

            // Column F (5): Zone 2 Catches
            row.createCell(5).setCellValue(p.zone2Catches.toDouble())

            // Column G (6): Zone 3 Catches
            row.createCell(6).setCellValue(p.zone3Catches.toDouble())

            // Column H (7): Zone 4 Catches
            row.createCell(7).setCellValue(p.zone4Catches.toDouble())

            // Column I (8): Finish on Sweet Spot (Y/N)
            row.createCell(8).setCellValue(if (p.finishOnSweetSpot) "Y" else "N")

            // Column K (10): Sweet Spot Bonus (Y if bonus >= 1)
            row.createCell(10).setCellValue(if (p.sweetSpotBonus >= 1) "Y" else "N")

            // Column L (11): Sweet Spot Bonus value
            row.createCell(11).setCellValue(p.sweetSpotBonus.toDouble())

            // Column M (12): Number of Misses
            row.createCell(12).setCellValue(p.numberOfMisses.toDouble())

            // Column P (15): All Rollers (Y/N)
            row.createCell(15).setCellValue(if (p.allRollers) "Y" else "N")
        }

        val bos = ByteArrayOutputStream()
        workbook.write(bos)
        workbook.close()
        return bos.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        return ByteArray(0)
    }
}

actual fun generateFourWayPlayXlsx(participants: List<FourWayPlayExportParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3
        participants.forEachIndexed { index, p ->
            val row = worksheet.getRow(startRow + index) ?: worksheet.createRow(startRow + index)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)
            row.createCell(4).setCellValue(p.zone1Catches.toDouble())
            row.createCell(5).setCellValue(p.zone2Catches.toDouble())
            row.createCell(6).setCellValue(p.zone3Catches.toDouble())
            row.createCell(7).setCellValue(p.zone4Catches.toDouble())

            // Flags are exported as Ints (0/1) to keep the expect/actual stable across platforms
            row.createCell(10).setCellValue(if (p.sweetSpot != 0) "Y" else "N")
            row.createCell(12).setCellValue(if (p.allRollers != 0) "Y" else "N")

            row.createCell(14).setCellValue(p.heightDivision)
            row.createCell(15).setCellValue(p.misses.toDouble())
        }
        ByteArrayOutputStream().use { bos ->
            workbook.write(bos)
            workbook.close()
            bos.toByteArray()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ByteArray(0)
    }
}

@Composable
actual fun rememberAssetLoader(): AssetLoader {
    val context = LocalContext.current
    return remember(context) {
        object : AssetLoader {
            override fun load(path: String): ByteArray? {
                val assetsPath = "assets/$path"

                // Try classpath first (commonMain/resources bundled in jar)
                try {
                    val stream = ImportedParticipant::class.java.classLoader?.getResourceAsStream(assetsPath)
                    if (stream != null) {
                        return stream.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    // ignore
                }

                // Try Android Assets
                return try {
                    context.assets.open(assetsPath).use { it.readBytes() }
                } catch (e: Exception) {
                    try {
                        // Try without assets prefix
                        context.assets.open(path).use { it.readBytes() }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                        null
                    }
                }
            }
        }
    }
}
