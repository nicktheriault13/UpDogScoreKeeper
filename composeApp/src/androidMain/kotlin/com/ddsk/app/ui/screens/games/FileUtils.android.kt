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
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale


@Composable
actual fun rememberFileExporter(): FileExporter {
    val context = LocalContext.current
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }

    val launcher = rememberLauncherForActivityResult(
        // Use XLSX MIME type so Android doesn't force a .xlsm extension.
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
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

        // Many UDC templates store participant rows on a named sheet.
        // Prefer that over sheet 0 to avoid reading a cover/instructions page.
        val sheet = workbook.getSheet("Data Entry Sheet")
            ?: workbook.getSheet("Data Entry")
            ?: workbook.getSheetAt(0)

        // Formula cells are common in templates; use evaluator so we extract display values.
        val evaluator = workbook.creationHelper.createFormulaEvaluator()

        for (row in sheet) {
            val rowData = mutableListOf<String>()
            val lastCellNum = row.lastCellNum.toInt().coerceAtLeast(0)
            for (i in 0 until lastCellNum) {
                val cell = row.getCell(i)
                val cellValue = if (cell == null) "" else {
                    when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> {
                            val num = cell.numericCellValue
                            if (num % 1.0 == 0.0) String.format(Locale.US, "%.0f", num) else num.toString()
                        }
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.FORMULA -> {
                            val evaluated = runCatching { evaluator.evaluate(cell) }.getOrNull()
                            when (evaluated?.cellType) {
                                CellType.STRING -> evaluated.stringValue
                                CellType.NUMERIC -> {
                                    val num = evaluated.numberValue
                                    if (num % 1.0 == 0.0) String.format(Locale.US, "%.0f", num) else num.toString()
                                }
                                CellType.BOOLEAN -> evaluated.booleanValue.toString()
                                else -> cell.toString()
                            }
                        }
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

            // Column P (15): Height Division
            if (p.heightDivision.isNotEmpty()) {
                row.createCell(15).setCellValue(p.heightDivision)
            }
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


actual fun generateGreedyXlsx(participants: List<GreedyParticipant>, templateBytes: ByteArray): ByteArray {
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
            compareByDescending<GreedyParticipant> { it.score }
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

            // Column P (15): Height Division
            row.createCell(15).setCellValue(p.heightDivision)

            // Column Q (16): All Rollers (Y/N)
            row.createCell(16).setCellValue(if (p.allRollers) "Y" else "N")
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

actual fun generateFireballXlsx(participants: List<FireballParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4

        // Debug: detect if all participants appear to have zero scoring.
        val allZero = participants.isNotEmpty() && participants.all {
            it.nonFireballPoints == 0 && it.fireballPoints == 0 && (it.highestZone ?: 0) == 0 && it.totalPoints == 0
        }
        if (allZero) {
            val row = worksheet.getRow(startRow) ?: worksheet.createRow(startRow)
            row.createCell(12).setCellValue("DEBUG: all participant totals are zero at export time")
        }

        val sorted = participants.sortedWith(
            compareByDescending<FireballParticipant> { it.totalPoints }
                .thenByDescending { it.highestZone ?: 0 }
                .thenByDescending { it.fireballPoints }
        )

        sorted.forEachIndexed { index, p ->
            val rowNum = startRow + index
            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)

            // Column E (4): Regular / non-fireball points
            val sweetSpot = p.sweetSpotBonus.coerceIn(0, 8)
            val adjustedNonFireball = when (sweetSpot) {
                4 -> (p.nonFireballPoints - 4).coerceAtLeast(0)
                else -> p.nonFireballPoints
            }
            row.createCell(4).setCellValue(adjustedNonFireball.toDouble())

            // Column F (5): Fireball points excluding sweet spot
            val adjustedFireball = when (sweetSpot) {
                8 -> (p.fireballPoints - 8).coerceAtLeast(0)
                else -> p.fireballPoints
            }
            row.createCell(5).setCellValue(adjustedFireball.toDouble())

            // Column G (6): Sweet spot points * 2
            row.createCell(6).setCellValue((sweetSpot * 2).toDouble())

            // Column H (7): Farthest zone caught / highest zone
            row.createCell(7).setCellValue((p.highestZone ?: 0).toDouble())

            // Column J (9): All Rollers (Y/N)
            row.createCell(9).setCellValue(if (p.allRollers) "Y" else "N")

            // Column K (10): Height Division
            row.createCell(10).setCellValue(p.heightDivision)
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

actual fun generateTimeWarpXlsx(participants: List<TimeWarpParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 4 // Excel row 5

        // Sort similarly to React: score desc, then timeRemaining (rounded) desc
        val sorted = participants.sortedWith(
            compareByDescending<TimeWarpParticipant> { it.result?.score ?: 0 }
                .thenByDescending { it.result?.timeRemaining?.roundToInt() ?: 0 }
        )

        var outIndex = 0
        sorted.forEach { p ->
            val r = p.result ?: return@forEach

            // Skip participants with no scoring data (align with React export behavior)
            val hasAnyData = r.score != 0 || r.misses != 0 || r.zonesCaught != 0 || r.sweetSpot || r.allRollers || r.timeRemaining != 60.0f
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex += 1

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column A (0): Level
            row.createCell(0).setCellValue(1.0)
            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)
            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)
            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)

            // Column E (4): Time Remaining (0.00)
            row.createCell(4).setCellValue(r.timeRemaining.toDouble())
            // Column F (5): Time Remaining Rounded
            row.createCell(5).setCellValue(r.timeRemaining.roundToInt().toDouble())
            // Column G (6): Misses
            row.createCell(6).setCellValue(r.misses.toDouble())
            // Column H (7): Zones Caught
            row.createCell(7).setCellValue(r.zonesCaught.toDouble())
            // Column I (8): Sweet Spot (Y/N)
            row.createCell(8).setCellValue(if (r.sweetSpot) "Y" else "N")
            // Column L (11): All Rollers (Y/N)
            row.createCell(11).setCellValue(if (r.allRollers) "Y" else "N")
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

actual fun generateThrowNGoXlsx(participants: List<ThrowNGoParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4 in Excel (0-based)

        // Sort same as React: score desc, misses asc, bonus desc
        val sorted = participants.sortedWith(
            compareByDescending<ThrowNGoParticipant> { it.results?.score ?: 0 }
                .thenBy { it.results?.misses ?: 0 }
                .thenByDescending { it.results?.bonusCatches ?: 0 }
        )

        var outIndex = 0
        sorted.forEach { p ->
            val r = p.results ?: return@forEach

            // Only export rows that have any scoring data
            val hasAnyData = r.score != 0 || r.catches != 0 || r.bonusCatches != 0 || r.misses != 0 || r.ob != 0 || r.sweetSpot || r.allRollers
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex += 1

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column A (0): Level
            row.createCell(0).setCellValue(1.0)
            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)
            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)
            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)

            // Column E (4): Catches
            row.createCell(4).setCellValue(r.catches.toDouble())
            // Column F (5): Bonus Catches
            row.createCell(5).setCellValue(r.bonusCatches.toDouble())
            // Column G (6): Misses
            row.createCell(6).setCellValue(r.misses.toDouble())

            // Column H (7): Score (template expects score excluding Sweet Spot points)
            val exportScore = if (r.sweetSpot) (r.score - 5).coerceAtLeast(0) else r.score
            row.createCell(7).setCellValue(exportScore.toDouble())

            // Column I (8): Sweet Spot (Y/N)
            row.createCell(8).setCellValue(if (r.sweetSpot) "Y" else "N")

            // Column K (10): All Rollers (Y/N)
            row.createCell(10).setCellValue(if (r.allRollers) "Y" else "N")

            // Column P (15): Height Division
            row.createCell(15).setCellValue(p.heightDivision)
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

actual fun generateSevenUpXlsm(participants: List<SevenUpParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4 (0-based)

        // Sort like React: finalScore desc, then timeRemaining desc
        val sorted = participants.sortedWith(
            compareByDescending<SevenUpParticipant> { it.jumpSum * 3 + it.nonJumpSum + it.timeRemaining.toInt() }
                .thenByDescending { it.timeRemaining }
        )

        var outIndex = 0
        sorted.forEach { p ->
            // Only export teams with any scoring data (align with other exports)
            val hasAnyData = p.jumpSum != 0 || p.nonJumpSum != 0 || p.timeRemaining.toInt() != 60 || p.sweetSpotBonus || p.allRollers
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex++

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column A (0): Level
            row.createCell(0).setCellValue(1.0)
            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)
            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)
            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)
            // Column E (4): Jump Sum
            row.createCell(4).setCellValue(p.jumpSum.toDouble())
            // Column G (6): Non-Jump Sum
            row.createCell(6).setCellValue(p.nonJumpSum.toDouble())
            // Column I (8): Time Remaining (0.00)
            row.createCell(8).setCellValue(p.timeRemaining.toDouble())
            // Column K (10): Sweet Spot (Y/N)
            row.createCell(10).setCellValue(if (p.sweetSpotBonus) "Y" else "N")
            // Column M (12): All Rollers (Y/N)
            row.createCell(12).setCellValue(if (p.allRollers) "Y" else "N")
            // Column P (15): Height Division
            row.createCell(15).setCellValue(p.heightDivision)
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

actual fun generateFunKeyXlsm(participants: List<FunKeyExportParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4 (0-based)

        participants.forEachIndexed { index, p ->
            val rowNum = startRow + index
            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column B (1): Handler
            row.createCell(1).setCellValue(p.handler)
            // Column C (2): Dog
            row.createCell(2).setCellValue(p.dog)
            // Column D (3): UTN
            row.createCell(3).setCellValue(p.utn)
            // Column E (4): Jump 3 Sum
            row.createCell(4).setCellValue(p.jump3Sum.toDouble())
            // Column F (5): Jump 2 Sum
            row.createCell(5).setCellValue(p.jump2Sum.toDouble())
            // Column G (6): Jump 1 + Tunnel Sum
            row.createCell(6).setCellValue(p.jump1TunnelSum.toDouble())
            // Column H (7): 1-Point Clicks
            row.createCell(7).setCellValue(p.onePointClicks.toDouble())
            // Column I (8): 2-Point Clicks
            row.createCell(8).setCellValue(p.twoPointClicks.toDouble())
            // Column J (9): 3-Point Clicks
            row.createCell(9).setCellValue(p.threePointClicks.toDouble())
            // Column K (10): 4-Point Clicks
            row.createCell(10).setCellValue(p.fourPointClicks.toDouble())
            // Column N (13): Sweet Spot (Y/N)
            row.createCell(13).setCellValue(p.sweetSpot)
            // Column P (15): All Rollers (Y/N)
            row.createCell(15).setCellValue(p.allRollers)
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

actual fun generateSpacedOutXlsx(participants: List<SpacedOutExportParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4 in Excel (0-based)

        // Sort like React: score desc, then spacedOut desc, then misses asc
        val sorted = participants.sortedWith(
            compareByDescending<SpacedOutExportParticipant> { itScore(it) }
                .thenByDescending { it.spacedOut }
                .thenBy { it.misses }
        )

        sorted.forEachIndexed { index, p ->
            val rowNum = startRow + index
            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)

            row.createCell(4).setCellValue(p.zonesCaught.toDouble())
            row.createCell(5).setCellValue(p.spacedOut.toDouble())
            row.createCell(6).setCellValue(p.misses.toDouble())

            // Column I (index 8): Sweet Spot (Y/N)
            row.createCell(8).setCellValue(if (p.sweetSpot != 0) "Y" else "N")

            // Column K (index 10): All Rollers (Y/N)
            row.createCell(10).setCellValue(if (p.allRollers != 0) "Y" else "N")

            // Column N (index 13): Clean Spaced Out (Y/N)
            val clean = (p.spacedOut >= 1 && p.misses == 0 && p.ob == 0)
            row.createCell(13).setCellValue(if (clean) "Y" else "N")

            // Column O (index 14): Height Division
            row.createCell(14).setCellValue(p.heightDivision)
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

private fun itScore(p: SpacedOutExportParticipant): Int {
    // Score isn't stored in SpacedOutExportParticipant; use zonesCaught*5 + spacedOut*25 + sweetSpot*5 (React rules)
    return p.zonesCaught * 5 + p.spacedOut * 25 + (if (p.sweetSpot != 0) 5 else 0)
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
