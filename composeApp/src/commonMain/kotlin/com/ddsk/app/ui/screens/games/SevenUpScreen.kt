package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

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

        var showTimeDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }

        if (showTimeDialog) {
            TimeInputDialog(screenModel) { showTimeDialog = false }
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("7-Up") })

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score: $score", style = MaterialTheme.typography.h5)
                Text("Time: ${timeRemaining.toDouble().formatTwoDecimals()}", style = MaterialTheme.typography.h5)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { if (isTimerRunning) screenModel.stopTimer() else screenModel.startTimer() }) {
                    Text(if (isTimerRunning) "Stop" else "Start")
                }
                Button(onClick = { showTimeDialog = true }) {
                    Text("Set Time")
                }
            }
            Spacer(Modifier.height(16.dp))

            // Import buttons
            val filePicker = rememberFilePicker { result ->
                scope.launch {
                    when (result) {
                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                        else -> {}
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { filePicker.launch() }) { Text("Import") }
                Button(onClick = { /* Export */ }) { Text("Export") }
            }
            Spacer(Modifier.height(16.dp))

            Button(onClick = { screenModel.cycleRectangleVersion() }) {
                Text("Version ${rectangleVersion + 1}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scoring Grid
            SevenUpGrid(screenModel)

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.flipField() }) { Text("Flip Field") }
                Button(onClick = { screenModel.reset() }) { Text("Reset") }
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

    Column(modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)) {
        val rows = 0..2
        val cols = if (isFlipped) 4 downTo 0 else 0..4

        rows.forEach { row ->
            Row(Modifier.height(if (row == 1) 80.dp else 40.dp)) {
                cols.forEach { col ->
                    val cellKey = "$row,$col"
                    val label = gridLayout[cellKey] ?: ""
                    val isJump = label.startsWith("Jump")
                    val nonJumpMarkValue = markedCells[cellKey]

                    val jumpDisabled = isJump && (label in disabledJumps || jumpStreak >= 3)
                    val nonJumpDisabled = !isJump && (!hasStarted || lastWasNonJump || nonJumpMarkValue != null || nonJumpMark > 5)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, MaterialTheme.colors.onSurface)
                    ) {
                        Button(
                            onClick = { screenModel.handleCellPress(label, row, col) },
                            enabled = !(jumpDisabled || nonJumpDisabled),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isJump) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label)
                                    Text((jumpCounts[label] ?: 0).toString())
                                }
                            } else if (nonJumpMarkValue != null) {
                                Text(nonJumpMarkValue.toString())
                            } else {
                                Text(label)
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
