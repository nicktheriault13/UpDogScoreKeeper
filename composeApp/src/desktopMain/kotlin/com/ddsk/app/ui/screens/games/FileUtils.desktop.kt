package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.ddsk.app.ui.screens.games.GreedyScreenModel
import com.ddsk.app.ui.screens.games.FarOutParticipant
import com.ddsk.app.ui.screens.games.FourWayPlayExportParticipant
import com.ddsk.app.ui.screens.games.ImportedParticipant
import com.ddsk.app.ui.screens.games.ImportResult
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    return object : FilePickerLauncher {
        override fun launch() {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("CSV or Excel", "csv", "xlsx", "xls", "xlsm")
                    isAcceptAllFileFilterUsed = true
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    if (file != null) {
                         try {
                            val bytes = file.readBytes()
                            val res = when (file.extension.lowercase()) {
                                "xlsx", "xlsm", "xls" -> ImportResult.Xlsx(bytes)
                                else -> ImportResult.Csv(bytes.decodeToString())
                            }
                            onResult(res)
                         } catch (e: Exception) {
                            onResult(ImportResult.Error(e.message ?: "Unknown error"))
                         }
                    } else {
                        onResult(ImportResult.Cancelled)
                    }
                } else {
                    onResult(ImportResult.Cancelled)
                }
            }
        }
    }
}

actual fun parseXlsx(bytes: ByteArray): List<ImportedParticipant> {
    val rows = parseXlsxRows(bytes)
    return extractParticipantsFromRows(rows)
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
                // Try to load as a resource from the classpath (for commonMain/resources)
                val resourcePath = "assets/$path"
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
                    ?: java.lang.Class.forName("com.ddsk.app.ui.screens.games.FileUtilsDesktopKt").classLoader.getResourceAsStream(resourcePath)

                if (stream != null) {
                    return stream.readBytes()
                }

                // Fallback to file system for development if needed
                val file = java.io.File(path)
                if (file.exists()) file.readBytes() else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
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

actual fun generateFireballXlsx(participants: List<FireballParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3

        val sorted = participants.sortedWith(
            compareByDescending<FireballParticipant> { it.totalPoints }
                .thenByDescending { it.highestZone ?: 0 }
                .thenByDescending { it.fireballPoints }
        )

        sorted.forEachIndexed { index, p ->
            val row = worksheet.getRow(startRow + index) ?: worksheet.createRow(startRow + index)
            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)

            val sweetSpot = p.sweetSpotBonus.coerceIn(0, 8)

            val adjustedNonFireball = when (sweetSpot) {
                4 -> (p.nonFireballPoints - 4).coerceAtLeast(0)
                else -> p.nonFireballPoints
            }
            row.createCell(4).setCellValue(adjustedNonFireball.toDouble())

            val adjustedFireball = when (sweetSpot) {
                8 -> (p.fireballPoints - 8).coerceAtLeast(0)
                else -> p.fireballPoints
            }
            row.createCell(5).setCellValue(adjustedFireball.toDouble())

            row.createCell(6).setCellValue((sweetSpot * 2).toDouble())
            row.createCell(7).setCellValue((p.highestZone ?: 0).toDouble())
            row.createCell(9).setCellValue(if (p.allRollers) "Y" else "N")
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
