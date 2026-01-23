package com.ddsk.app.ui.screens.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
actual fun rememberFilePicker(onResult: (ImportResult) -> Unit): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    return object : FilePickerLauncher {
        override fun launch() {
            scope.launch {
                // On Desktop, Dispatchers.Main is not guaranteed to exist, and Swing UI must run on the EDT.
                val selected = withContext(Dispatchers.IO) {
                    runOnSwingEdt {
                        val chooser = JFileChooser().apply {
                            fileFilter = FileNameExtensionFilter("CSV or Excel", "csv", "xlsx", "xls", "xlsm")
                            isAcceptAllFileFilterUsed = true
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                    }
                }

                if (selected == null) {
                    onResult(ImportResult.Cancelled)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    try {
                        val bytes = selected.readBytes()
                        val res = when (selected.extension.lowercase()) {
                            "xlsx", "xlsm", "xls" -> ImportResult.Xlsx(bytes)
                            else -> ImportResult.Csv(bytes.decodeToString())
                        }
                        onResult(res)
                    } catch (e: Exception) {
                        onResult(ImportResult.Error(e.message ?: "Unknown error"))
                    }
                }
            }
        }
    }
}

private fun <T> runOnSwingEdt(block: () -> T): T {
    if (EventQueue.isDispatchThread()) return block()
    val ref = AtomicReference<T>()
    EventQueue.invokeAndWait {
        ref.set(block())
    }
    return ref.get()
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
                    ?: Class.forName("com.ddsk.app.ui.screens.games.FileUtilsDesktopKt")
                        .classLoader
                        .getResourceAsStream(resourcePath)

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

actual fun generateTimeWarpXlsx(participants: List<TimeWarpParticipant>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 4 // Excel row 5

        val sorted = participants.sortedWith(
            compareByDescending<TimeWarpParticipant> { it.result?.score ?: 0 }
                .thenByDescending { it.result?.timeRemaining?.roundToInt() ?: 0 }
        )

        var outIndex = 0
        sorted.forEach { p ->
            val r = p.result ?: return@forEach
            val hasAnyData = r.score != 0 || r.misses != 0 || r.zonesCaught != 0 || r.sweetSpot || r.allRollers || r.timeRemaining != 60.0f
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex++

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)
            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)

            row.createCell(4).setCellValue(r.timeRemaining.toDouble())
            row.createCell(5).setCellValue(r.timeRemaining.roundToInt().toDouble())
            row.createCell(6).setCellValue(r.misses.toDouble())
            row.createCell(7).setCellValue(r.zonesCaught.toDouble())
            row.createCell(8).setCellValue(if (r.sweetSpot) "Y" else "N")
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
        val startRow = 3 // Row 4 (0-based)

        val sorted = participants.sortedWith(
            compareByDescending<ThrowNGoParticipant> { it.results?.score ?: 0 }
                .thenBy { it.results?.misses ?: 0 }
                .thenByDescending { it.results?.bonusCatches ?: 0 }
        )

        var outIndex = 0
        sorted.forEach { p ->
            val r = p.results ?: return@forEach
            val hasAnyData = r.score != 0 || r.catches != 0 || r.bonusCatches != 0 || r.misses != 0 || r.ob != 0 || r.sweetSpot || r.allRollers
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex += 1

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)
            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)

            row.createCell(4).setCellValue(r.catches.toDouble())
            row.createCell(5).setCellValue(r.bonusCatches.toDouble())
            row.createCell(6).setCellValue(r.misses.toDouble())

            val exportScore = if (r.sweetSpot) (r.score - 5).coerceAtLeast(0) else r.score
            row.createCell(7).setCellValue(exportScore.toDouble())

            row.createCell(8).setCellValue(if (r.sweetSpot) "Y" else "N")
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

actual fun generateFrizgilityXlsx(participants: List<FrizgilityParticipantWithResults>, templateBytes: ByteArray): ByteArray {
    return try {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(templateBytes))
        val worksheet = workbook.getSheet("Data Entry Sheet") ?: workbook.getSheetAt(0)
        val startRow = 3 // Row 4 (0-based)

        // Filter out participants with no scoring data
        val participantsWithData = participants.filter { p ->
            p.obstacle1 + p.obstacle2 + p.obstacle3 + p.catch10plus + p.catch3to10 + p.misses > 0 || p.sweetSpot
        }

        // If no data to export, return the template as-is
        if (participantsWithData.isEmpty()) {
            ByteArrayOutputStream().use { bos ->
                workbook.write(bos)
                workbook.close()
                return bos.toByteArray()
            }
        }

        // Calculate scores for each participant
        val participantsWithScores = participantsWithData.map { p ->
            val obstacle1 = p.obstacle1
            val obstacle2 = p.obstacle2
            val obstacle3 = p.obstacle3
            val fail1 = p.fail1
            val fail2 = p.fail2
            val fail3 = p.fail3
            val failTotal = fail1 + fail2 + fail3
            val catch10plus = p.catch10plus
            val catch3to10 = p.catch3to10
            val misses = p.misses
            val sweetSpot = p.sweetSpot

            // Calculate score: (obstacles * 5) + (catch10plus * 10) + (catch3to10 * 3) + (sweetSpot ? 10 : 0)
            val finalScore = (obstacle1 + obstacle2 + obstacle3) * 5 + (catch10plus * 10) + (catch3to10 * 3) + (if (sweetSpot) 10 else 0)

            Triple(p, failTotal, finalScore)
        }

        // Sort by score (highest first), then by misses (lowest first), then by failTotal (lowest first)
        val sorted = participantsWithScores.sortedWith(
            compareByDescending<Triple<FrizgilityParticipantWithResults, Int, Int>> { it.third }
                .thenBy { it.first.misses }
                .thenBy { it.second }
        )

        sorted.forEachIndexed { index, (p, failTotal, finalScore) ->
            val rowNum = startRow + index

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)

            // Column A (0): Level (1 for Level 1)
            val cellA = row.getCell(0) ?: row.createCell(0)
            cellA.setCellValue(1.0)

            // Column B (1): Handler
            val cellB = row.getCell(1) ?: row.createCell(1)
            cellB.setCellValue(p.handler)

            // Column C (2): Dog
            val cellC = row.getCell(2) ?: row.createCell(2)
            cellC.setCellValue(p.dog)

            // Column D (3): UTN
            val cellD = row.getCell(3) ?: row.createCell(3)
            cellD.setCellValue(p.utn)

            // Column E (4): Obstacle 1 count
            val cellE = row.getCell(4) ?: row.createCell(4)
            cellE.setCellValue(p.obstacle1.toDouble())

            // Column F (5): Obstacle 2 count
            val cellF = row.getCell(5) ?: row.createCell(5)
            cellF.setCellValue(p.obstacle2.toDouble())

            // Column G (6): Obstacle 3 count
            val cellG = row.getCell(6) ?: row.createCell(6)
            cellG.setCellValue(p.obstacle3.toDouble())

            // Column H (7): Missed obstacles (fail total)
            val cellH = row.getCell(7) ?: row.createCell(7)
            cellH.setCellValue(failTotal.toDouble())

            // Column I (8): 10+ Catch count
            val cellI = row.getCell(8) ?: row.createCell(8)
            cellI.setCellValue(p.catch10plus.toDouble())

            // Column J (9): 3-10 Catch count
            val cellJ = row.getCell(9) ?: row.createCell(9)
            cellJ.setCellValue(p.catch3to10.toDouble())

            // Column K (10): Missed/OB catches
            val cellK = row.getCell(10) ?: row.createCell(10)
            cellK.setCellValue(p.misses.toDouble())

            // Column L (11): Sweet Spot (Y/N)
            val cellL = row.getCell(11) ?: row.createCell(11)
            cellL.setCellValue(if (p.sweetSpot) "Y" else "N")

            // Column N (13): Perfect Round (Y/N)
            // Y if: obstacle1>=4, obstacle2>=4, obstacle3>=4, catch10plus+catch3to10>=4, failTotal=0, misses=0
            val catchTotal = p.catch10plus + p.catch3to10
            val isPerfectRound = p.obstacle1 >= 4 && p.obstacle2 >= 4 && p.obstacle3 >= 4 &&
                                 catchTotal >= 4 && failTotal == 0 && p.misses == 0
            val cellN = row.getCell(13) ?: row.createCell(13)
            cellN.setCellValue(if (isPerfectRound) "Y" else "N")

            // Column O (14): Perfect Round with 5 (Y/N)
            // Y if: obstacle1>=5, obstacle2>=5, obstacle3>=5, catch10plus+catch3to10>=5, failTotal=0, misses=0
            val isPerfectRound5 = p.obstacle1 >= 5 && p.obstacle2 >= 5 && p.obstacle3 >= 5 &&
                                  catchTotal >= 5 && failTotal == 0 && p.misses == 0
            val cellO = row.getCell(14) ?: row.createCell(14)
            cellO.setCellValue(if (isPerfectRound5) "Y" else "N")

            // Column P (15): Height Division
            val cellP = row.getCell(15) ?: row.createCell(15)
            cellP.setCellValue(p.heightDivision)
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

        val sorted = participants.sortedWith(
            compareByDescending<SevenUpParticipant> { it.jumpSum * 3 + it.nonJumpSum + it.timeRemaining.toInt() }
                .thenByDescending { it.timeRemaining }
        )

        var outIndex = 0
        sorted.forEach { p ->
            val hasAnyData = p.jumpSum != 0 || p.nonJumpSum != 0 || p.timeRemaining.toInt() != 60 || p.sweetSpotBonus || p.allRollers
            if (!hasAnyData) return@forEach

            val rowNum = startRow + outIndex
            outIndex++

            val row = worksheet.getRow(rowNum) ?: worksheet.createRow(rowNum)
            row.createCell(0).setCellValue(1.0)
            row.createCell(1).setCellValue(p.handler)
            row.createCell(2).setCellValue(p.dog)
            row.createCell(3).setCellValue(p.utn)
            row.createCell(4).setCellValue(p.jumpSum.toDouble())
            row.createCell(6).setCellValue(p.nonJumpSum.toDouble())
            row.createCell(8).setCellValue(p.timeRemaining.toDouble())
            row.createCell(10).setCellValue(if (p.sweetSpotBonus) "Y" else "N")
            row.createCell(12).setCellValue(if (p.allRollers) "Y" else "N")
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
    return p.zonesCaught * 5 + p.spacedOut * 25 + (if (p.sweetSpot != 0) 5 else 0)
}
