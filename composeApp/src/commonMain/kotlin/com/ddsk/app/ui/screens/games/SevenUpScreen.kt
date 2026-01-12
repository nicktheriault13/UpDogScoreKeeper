package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import com.ddsk.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Boom-like vibrant palette
private val primaryBlue = Color(0xFF2979FF)
private val infoCyan = Color(0xFF00B8D4)
private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val boomPink = Color(0xFFF500A1)
private val errorRed = Color(0xFFD50000)
private val disabledBackground = Color(0xFFF1F1F1)
private val disabledContent = Color(0xFF222222)

@Composable
private fun vibrantButtonColors(background: Color, content: Color): ButtonColors {
    return ButtonDefaults.buttonColors(
        backgroundColor = background,
        contentColor = content,
        disabledBackgroundColor = disabledBackground,
        disabledContentColor = disabledContent
    )
}

object SevenUpScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SevenUpScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val score by screenModel.score.collectAsState()
        val timeRemaining by screenModel.timeRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val rectangleVersion by screenModel.rectangleVersion.collectAsState()
        val isFlipped by screenModel.isFlipped.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantsQueue by screenModel.participants.collectAsState()
        val allRollers by screenModel.allRollers.collectAsState()

        val fileExporter = rememberFileExporter()
        val assetLoader = rememberAssetLoader()

        val scope = rememberCoroutineScope()

        var showTimeDialog by remember { mutableStateOf(false) }
        var showClearParticipantsDialog by remember { mutableStateOf(false) }

        // Import: match Fireball UX (Add vs Replace)
        var pendingImport by remember { mutableStateOf<ImportResult?>(null) }
        var showImportChoiceDialog by remember { mutableStateOf(false) }

        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImport = result
                    showImportChoiceDialog = true
                }
                else -> {}
            }
        }

        if (showImportChoiceDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportChoiceDialog = false
                    pendingImport = null
                },
                title = { Text("Import participants") },
                text = { Text("Add to existing list, or replace the current list?") },
                confirmButton = {
                    TextButton(onClick = {
                        val import = pendingImport
                        showImportChoiceDialog = false
                        pendingImport = null
                        if (import != null) {
                            scope.launch {
                                when (import) {
                                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, SevenUpScreenModel.ImportMode.Add)
                                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, SevenUpScreenModel.ImportMode.Add)
                                    else -> {}
                                }
                            }
                        }
                    }) { Text("Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            val import = pendingImport
                            showImportChoiceDialog = false
                            pendingImport = null
                            if (import != null) {
                                scope.launch {
                                    when (import) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, SevenUpScreenModel.ImportMode.ReplaceAll)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, SevenUpScreenModel.ImportMode.ReplaceAll)
                                        else -> {}
                                    }
                                }
                            }
                        }) { Text("Replace") }
                        TextButton(onClick = {
                            showImportChoiceDialog = false
                            pendingImport = null
                        }) { Text("Cancel") }
                    }
                }
            )
        }

        if (showClearParticipantsDialog) {
            AlertDialog(
                onDismissRequest = { showClearParticipantsDialog = false },
                title = { Text("Clear all participants?") },
                text = { Text("This will remove all loaded teams.") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.clearParticipants()
                        showClearParticipantsDialog = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearParticipantsDialog = false }) { Text("Cancel") }
                }
            )
        }

        // JSON export (auto on Next)
        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("7-Up") })
        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audioPlayer.play() else audioPlayer.stop()
        }

        if (showTimeDialog) {
            TimeInputDialog(screenModel) { showTimeDialog = false }
        }

        val remainingParticipants = participantsQueue

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val spacing = 12.dp
                val sidebarWidth = 190.dp
                val participantsWidth = 220.dp
                val minFieldWidth = 280.dp
                val availableFieldWidth = (maxWidth - sidebarWidth - participantsWidth - spacing * 2)
                    .coerceAtLeast(minFieldWidth)
                val fieldHeight = (availableFieldWidth / 2.2f).coerceAtLeast(320.dp)

                Column(modifier = Modifier.fillMaxSize()) {
                    // --- Header (2 rows) ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Row 1: Team + Timer audio start/stop + manual time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${activeParticipant?.handler.orEmpty()}${activeParticipant?.dog?.let { " & $it" }.orEmpty()}".ifBlank { "No team loaded" },
                                    style = MaterialTheme.typography.h6,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Button(
                                    onClick = {
                                        if (isTimerRunning) {
                                            screenModel.logEvent("Timer Stop")
                                            screenModel.stopTimer()
                                        } else {
                                            screenModel.logEvent("Timer Start")
                                            screenModel.startTimer()
                                        }
                                    },
                                    colors = vibrantButtonColors(
                                        background = if (isTimerRunning) errorRed else successGreen,
                                        content = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (isTimerRunning) "Stop" else "Start", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        screenModel.logEvent("Set Time dialog")
                                        showTimeDialog = true
                                    },
                                    colors = vibrantButtonColors(infoCyan, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Set Time", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Row 2: Score / Time / Version
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Score: $score", style = MaterialTheme.typography.h6)
                                Text("Time: ${timeRemaining.toDouble().formatTwoDecimals()}", style = MaterialTheme.typography.h6)

                                Button(
                                    onClick = {
                                        screenModel.logEvent("Version ${rectangleVersion + 2}")
                                        screenModel.cycleRectangleVersion()
                                    },
                                    enabled = !screenModel.hasStarted.value,
                                    colors = vibrantButtonColors(primaryBlue, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Version ${rectangleVersion + 1}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = fieldHeight),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.Top
                    ) {
                        // --- Sidebar: Import/Export/Actions ---
                        Card(
                            modifier = Modifier.width(sidebarWidth),
                            shape = RoundedCornerShape(16.dp),
                            elevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Actions", fontWeight = FontWeight.Bold)

                                Button(
                                    onClick = {
                                        screenModel.logEvent("Import")
                                        filePicker.launch()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(infoCyan, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Import", fontWeight = FontWeight.Bold) }

                                Button(
                                    onClick = {
                                        screenModel.logEvent("Export")
                                        scope.launch {
                                            val templateBytes = assetLoader.load("templates/UDC SevenUP Data Entry L1 Div Sort.xlsm")
                                            if (templateBytes == null) return@launch

                                            val exportBytes = screenModel.exportScoresXlsm(templateBytes)
                                            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                            val filename = "7Up_Scores_${screenModel.exportStampForFilename(now)}.xlsm"
                                            fileExporter.save(filename, exportBytes)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(primaryBlue, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Export", fontWeight = FontWeight.Bold) }

                                Divider()

                                Button(
                                    onClick = {
                                        screenModel.logEvent("All Rollers toggle")
                                        screenModel.toggleAllRollersFlag()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(
                                        background = if (allRollers) successGreen else primaryBlue,
                                        content = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(if (allRollers) "All Rollers: ON" else "All Rollers: OFF", fontWeight = FontWeight.Bold) }

                                Button(
                                    onClick = {
                                        screenModel.logEvent("Flip Field")
                                        screenModel.flipField()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(boomPink, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(if (isFlipped) "Flip Field: ON" else "Flip Field", fontWeight = FontWeight.Bold) }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { screenModel.previousParticipant() },
                                        enabled = participantsQueue.size + (if (activeParticipant != null) 1 else 0) > 1,
                                        modifier = Modifier.weight(1f),
                                        colors = vibrantButtonColors(boomPink, Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Previous", fontWeight = FontWeight.Bold) }

                                    Button(
                                        onClick = { screenModel.skipParticipant() },
                                        enabled = activeParticipant != null && participantsQueue.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        colors = vibrantButtonColors(warningOrange, Color(0xFF442800)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Skip", fontWeight = FontWeight.Bold) }
                                }

                                Button(
                                    onClick = { screenModel.nextParticipant() },
                                    enabled = activeParticipant != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(successGreen, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Next", fontWeight = FontWeight.Bold) }

                                Button(
                                    onClick = {
                                        screenModel.logEvent("Reset")
                                        screenModel.reset()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = vibrantButtonColors(errorRed, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Reset", fontWeight = FontWeight.Bold) }

                                TextButton(
                                    onClick = { showClearParticipantsDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Clear Teams", color = errorRed, fontWeight = FontWeight.Bold) }
                            }
                        }

                        // --- Remaining teams (Boom-like card) ---
                        Card(
                            modifier = Modifier.width(participantsWidth).fillMaxHeight(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = 6.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                Text("Remaining (${remainingParticipants.size})", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Divider()
                                Spacer(Modifier.height(8.dp))
                                if (remainingParticipants.isEmpty()) {
                                    Text("No teams loaded")
                                } else {
                                    remainingParticipants.take(30).forEachIndexed { idx, p ->
                                        val isActive = p == activeParticipant
                                        Text(
                                            text = "${p.handler} & ${p.dog}",
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) primaryBlue else MaterialTheme.colors.onSurface
                                        )
                                        if (idx < remainingParticipants.lastIndex) Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                        }

                        // --- Scoring grid ---
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SevenUpGrid(screenModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeInputDialog(screenModel: SevenUpScreenModel, onDismiss: () -> Unit) {
    var timeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Time Remaining") },
        text = {
            TextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                label = { Text("e.g., 42.5") }
            )
        },
        confirmButton = {
            Button(onClick = {
                screenModel.logEvent("Manual Time set to $timeInput")
                screenModel.setTimeManually(timeInput)
                onDismiss()
            }) { Text("Set") }
        }
    )
}

@Composable
private fun SevenUpGrid(screenModel: SevenUpScreenModel) {
    val isFlipped by screenModel.isFlipped.collectAsState()
    val rectangleVersion by screenModel.rectangleVersion.collectAsState()
    val jumpCounts by screenModel.jumpCounts.collectAsState()
    val disabledJumps by screenModel.disabledJumps.collectAsState()
    val markedCells by screenModel.markedCells.collectAsState()
    val jumpStreak by screenModel.jumpStreak.collectAsState()
    val hasStarted by screenModel.hasStarted.collectAsState()
    val lastWasNonJump by screenModel.lastWasNonJump.collectAsState()
    val nonJumpMark by screenModel.nonJumpMark.collectAsState()

    val gridLayout = getGridLayout(rectangleVersion)

    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 16.dp
            val interCellSpacing = 12.dp
            val gridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val widthLimitedCell = ((gridWidth - interCellSpacing * 4) / 5).coerceAtLeast(40.dp)

            // If parent gives us a finite height, cap cell size by height as Boom does.
            val hasFiniteHeight = maxHeight != Dp.Unspecified && maxHeight != Dp.Infinity && maxHeight > 0.dp
            val heightLimitedCell = if (hasFiniteHeight) {
                // 3 rows total, with middle row ~1.8x height. Total row-heights ~= 3.8x cell.
                val totalVerticalSpacing = interCellSpacing * 2
                ((maxHeight - totalVerticalSpacing) / 3.8f).coerceAtLeast(36.dp)
            } else {
                widthLimitedCell
            }

            val cellSize = minOf(widthLimitedCell, heightLimitedCell)
            val rowOrder = if (isFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val columnOrder = if (isFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(interCellSpacing)
            ) {
                rowOrder.forEach { row ->
                    val rowHeight = if (row == 1) cellSize * 1.8f else cellSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(interCellSpacing)
                    ) {
                        columnOrder.forEach { col ->
                            val cellKey = "$row,$col"
                            val label = gridLayout[cellKey] ?: ""
                            val isJump = label.startsWith("Jump")
                            val nonJumpMarkValue = markedCells[cellKey]

                            val jumpDisabled = isJump && (label in disabledJumps || jumpStreak >= 3)
                            val nonJumpDisabled = !isJump && (!hasStarted || lastWasNonJump || nonJumpMarkValue != null || nonJumpMark > 5)

                            val backgroundColor = when {
                                isJump -> boomPink
                                nonJumpMarkValue != null -> successGreen
                                else -> primaryBlue
                            }
                            val contentColor = if (!isJump && nonJumpMarkValue != null && label.isBlank()) Color.Black else Color.White

                            Button(
                                onClick = {
                                    screenModel.logEvent("Grid press: '$label' ($row,$col)")
                                    screenModel.handleCellPress(label, row, col)
                                },
                                enabled = !(jumpDisabled || nonJumpDisabled),
                                colors = vibrantButtonColors(backgroundColor, contentColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (isJump) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(label, fontWeight = FontWeight.Bold)
                                        Text((jumpCounts[label] ?: 0).toString(), fontWeight = FontWeight.Bold)
                                    }
                                } else if (nonJumpMarkValue != null && label.isBlank()) {
                                    Text(nonJumpMarkValue.toString(), fontWeight = FontWeight.Bold, color = Color.Black)
                                } else if (nonJumpMarkValue != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (label.isNotBlank()) Text(label, fontWeight = FontWeight.Bold)
                                        Text(nonJumpMarkValue.toString(), fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getGridLayout(version: Int): Map<String, String> {
    return when (version) {
        0 -> mapOf(
            "1,2" to "Sweet Spot", "1,0" to "Jump1", "2,0" to "Jump2", "0,1" to "Jump3",
            "2,1" to "Jump4", "1,3" to "Jump5", "2,3" to "Jump6", "0,4" to "Jump7"
        )
        1 -> mapOf(
            "0,0" to "Jump1", "0,1" to "Jump2", "1,1" to "Jump3", "2,1" to "Jump4",
            "1,2" to "Sweet Spot", "1,3" to "Jump5", "2,3" to "Jump6", "0,4" to "Jump7"
        )
        2 -> mapOf(
            "1,2" to "Sweet Spot", "0,0" to "Jump1", "1,0" to "Jump2", "0,1" to "Jump3",
            "0,3" to "Jump4", "2,3" to "Jump5", "1,4" to "Jump6", "2,4" to "Jump7"
        )
        3 -> mapOf(
            "1,2" to "Sweet Spot", "1,0" to "Jump1", "0,1" to "Jump2", "1,1" to "Jump3",
            "2,2" to "Jump4", "1,3" to "Jump5", "1,4" to "Jump6", "2,4" to "Jump7"
        )
        4 -> mapOf(
            "1,2" to "Sweet Spot", "0,0" to "Jump1", "2,0" to "Jump2", "1,1" to "Jump3",
            "0,2" to "Jump4", "1,3" to "Jump5", "0,4" to "Jump6", "2,4" to "Jump7"
        )
        5 -> mapOf(
            "1,2" to "Sweet Spot", "2,0" to "Jump1", "1,1" to "Jump2", "2,1" to "Jump3",
            "0,2" to "Jump4", "1,3" to "Jump5", "2,3" to "Jump6", "2,4" to "Jump7"
        )
        6 -> mapOf(
            "1,2" to "Sweet Spot", "2,0" to "Jump1", "0,1" to "Jump2", "1,1" to "Jump3",
            "2,1" to "Jump4", "0,3" to "Jump5", "0,4" to "Jump6", "1,4" to "Jump7"
        )
        7 -> mapOf(
            "1,2" to "Sweet Spot", "0,0" to "Jump1", "2,0" to "Jump2", "1,1" to "Jump3",
            "0,2" to "Jump4"
        )
        8 -> mapOf(
            "1,2" to "Sweet Spot", "0,2" to "Jump1", "1,3" to "Jump2", "2,4" to "Jump3"
        )
        9 -> mapOf(
            "1,2" to "Sweet Spot", "0,1" to "Jump1", "1,1" to "Jump2", "2,1" to "Jump3"
        )
        10 -> mapOf(
            "1,2" to "Sweet Spot", "2,2" to "Jump1", "1,3" to "Jump2", "1,4" to "Jump3",
            "2,4" to "Jump4"
        )
        else -> emptyMap()
    }
}

fun Double.formatTwoDecimals(): String {
    // Multiplatform-safe formatting (avoids JVM String.format)
    val scaled = kotlin.math.round(this * 100.0) / 100.0
    val text = scaled.toString()
    val dot = text.indexOf('.')
    return when {
        dot < 0 -> "$text.00"
        text.length - dot - 1 == 0 -> "${text}00"
        text.length - dot - 1 == 1 -> "${text}0"
        else -> text
    }
}
