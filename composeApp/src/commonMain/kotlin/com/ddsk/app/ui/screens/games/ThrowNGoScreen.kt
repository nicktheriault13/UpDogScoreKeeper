package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.MaterialTheme
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.ui.components.GameHomeButton

object ThrowNGoScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ThrowNGoScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()

        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }

        var showImportModeDialog by remember { mutableStateOf(false) }
        var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }

        // Import / Export helpers
        val assetLoader = rememberAssetLoader()
        val exporter = rememberFileExporter()

        // When model emits a pending JSON export, prompt user to save it.
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImportResult = result
                    showImportModeDialog = true
                }
                else -> { /* ignore */ }
            }
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Throw N Go") })

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Home button is rendered inside the score card to avoid overlap.

                val columnSpacing = 16.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(columnSpacing),
                    horizontalArrangement = Arrangement.spacedBy(columnSpacing)
                ) {
                    // Left (main gameplay) column
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        ThrowNGoScoreSummaryCard(navigator = navigator, uiState = uiState)

                        Box(modifier = Modifier.weight(1f)) {
                            ScoringGrid(onScore = screenModel::recordThrow)
                        }

                        ThrowNGoControlRow(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onTimerToggle = { if (timerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            onTimerReset = screenModel::resetTimer,
                            onUndo = screenModel::undo,
                            onSweetSpot = screenModel::toggleSweetSpot,
                            sweetSpotActive = uiState.scoreState.sweetSpotActive,
                            onAllRollers = screenModel::toggleAllRollers,
                            allRollersActive = uiState.scoreState.allRollersActive,
                            onMiss = screenModel::incrementMiss,
                            onOb = screenModel::incrementOb,
                            onFlipField = screenModel::flipField,
                            onNext = screenModel::nextParticipant,
                            onSkip = screenModel::skipParticipant
                        )
                    }

                    // Right (queue + import/export) column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        ThrowNGoTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartStop = { if (timerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            onReset = screenModel::resetTimer
                        )

                        ParticipantList(uiState = uiState, modifier = Modifier.fillMaxWidth().weight(1f))

                        ThrowNGoImportExportCard(
                            onImportClick = { filePicker.launch() },
                            onExportClick = {
                                val template = assetLoader.load("templates/UDC Throw N Go Data Entry L1 or L2 Div Sort.xlsx")
                                if (template != null) {
                                    val bytes = screenModel.exportScoresXlsx(template)
                                    exporter.save("ThrowNGo_Scores.xlsx", bytes)
                                }
                            },
                            onExportLogClick = {
                                val content = screenModel.exportLog()
                                scope.launch {
                                    saveJsonFileWithPicker("ThrowNGo_Log.txt", content)
                                }
                            },
                            onAddTeamClick = { showAddParticipant = true },
                            onPreviousClick = screenModel::previousParticipant,
                            onClearClick = screenModel::clearParticipants
                        )
                    }
                }
            }
        }

        if (showAddParticipant) {
            ParticipantDialog(
                onDismiss = { showAddParticipant = false },
                onConfirm = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    showAddParticipant = false
                }
            )
        }

        if (showImportModeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportModeDialog = false
                    pendingImportResult = null
                },
                title = { Text("Import participants") },
                text = { Text("Would you like to add these participants to the current list, or replace everything?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val import = pendingImportResult
                            if (import is ImportResult.Csv) {
                                screenModel.importParticipantsFromCsv(import.contents, ThrowNGoScreenModel.ImportMode.Add)
                            } else if (import is ImportResult.Xlsx) {
                                screenModel.importParticipantsFromXlsx(import.bytes, ThrowNGoScreenModel.ImportMode.Add)
                            }
                            showImportModeDialog = false
                            pendingImportResult = null
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val import = pendingImportResult
                                if (import is ImportResult.Csv) {
                                    screenModel.importParticipantsFromCsv(import.contents, ThrowNGoScreenModel.ImportMode.ReplaceAll)
                                } else if (import is ImportResult.Xlsx) {
                                    screenModel.importParticipantsFromXlsx(import.bytes, ThrowNGoScreenModel.ImportMode.ReplaceAll)
                                }
                                showImportModeDialog = false
                                pendingImportResult = null
                            }
                        ) { Text("Replace") }
                        TextButton(
                            onClick = {
                                showImportModeDialog = false
                                pendingImportResult = null
                            }
                        ) { Text("Cancel") }
                    }
                }
            )
        }
    }
}

@Composable
private fun ThrowNGoScoreSummaryCard(navigator: cafe.adriel.voyager.navigator.Navigator, uiState: ThrowNGoUiState) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameHomeButton(navigator = navigator)
                Text(text = "Score: ${uiState.scoreState.score}", style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Catches: ${uiState.scoreState.catches} • Bonus: ${uiState.scoreState.bonusCatches}",
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFF49454F)
                )
            }

            val active = uiState.activeParticipant
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Misses: ${uiState.scoreState.misses} • OB: ${uiState.scoreState.ob}",
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFF49454F)
                )
                Text(
                    text = active?.let { "Active: ${it.handler} & ${it.dog}" } ?: "No active team",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThrowNGoTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    onReset: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartStop,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) Color(0xFFFFB74D) else Color(0xFF2196F3))
            ) {
                Text(if (timerRunning) "${timeLeft}s" else "Timer", color = Color.White)
            }
            Button(onClick = onReset, enabled = !timerRunning) { Text("Reset") }
        }
    }
}

@Composable
private fun ThrowNGoImportExportCard(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onExportLogClick: () -> Unit,
    onAddTeamClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Actions", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onImportClick, modifier = Modifier.weight(1f)) { Text("Import") }
                Button(onClick = onExportClick, modifier = Modifier.weight(1f)) { Text("Export") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportLogClick, modifier = Modifier.weight(1f)) { Text("Log") }
                Button(onClick = onAddTeamClick, modifier = Modifier.weight(1f)) { Text("Add") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPreviousClick, modifier = Modifier.weight(1f)) { Text("Previous") }
                Button(onClick = onClearClick, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ThrowNGoControlRow(
    timerRunning: Boolean,
    timeLeft: Int,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit,
    onUndo: () -> Unit,
    onSweetSpot: () -> Unit,
    sweetSpotActive: Boolean,
    onAllRollers: () -> Unit,
    allRollersActive: Boolean,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    onFlipField: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTimerToggle,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) Color(0xFFFFB74D) else Color(0xFF2196F3))
            ) {
                Text(if (timerRunning) "${timeLeft}s" else "Timer", color = Color.White)
            }
            Button(onClick = onTimerReset, enabled = !timerRunning) { Text("Reset") }

            Button(onClick = onUndo) { Text("Undo") }

            Button(
                onClick = onSweetSpot,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (sweetSpotActive) Color(0xFF00C853) else Color(0xFFFF9100))
            ) {
                Text("Sweet Spot", color = Color.White)
            }

            Button(
                onClick = onAllRollers,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (allRollersActive) Color(0xFF00C853) else Color(0xFF2979FF))
            ) {
                Text("All Rollers", color = Color.White)
            }

            Button(onClick = onMiss, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD50000))) {
                Text("Miss", color = Color.White)
            }
            Button(onClick = onOb, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFFB74D))) {
                Text("OB", color = Color(0xFF442800))
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = onFlipField) { Text("Flip") }
            Button(onClick = onNext) { Text("Next") }
            Button(onClick = onSkip) { Text("Skip") }
        }
    }
}

// Log display intentionally removed from the gameplay screen.

@Composable
private fun ScoringGrid(onScore: (Int, Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), elevation = 8.dp, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rows = listOf(
                listOf(1, 3, 4, 5, 6),
                listOf(1, 6, 8, 10, 12),
                listOf(1, 3, 4, 5, 6)
            )

            rows.forEachIndexed { rowIndex, rowValues ->
                val isBonusRow = rowIndex == 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isBonusRow) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowValues.forEach { value ->
                        Button(
                            onClick = { onScore(value, isBonusRow) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFF500A1),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Text("$value", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantDialog(
    onDismiss: () -> Unit,
    onConfirm: (handler: String, dog: String, utn: String) -> Unit
) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") })
                OutlinedTextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") })
                OutlinedTextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(handler.trim(), dog.trim(), utn.trim()) },
                enabled = handler.isNotBlank() && dog.isNotBlank() && utn.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ParticipantList(uiState: ThrowNGoUiState, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Remaining (${uiState.queue.size})", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Queue list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.queue) { participant ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(participant.displayName, fontWeight = FontWeight.SemiBold)
                        if (participant.utn.isNotBlank()) {
                            Text(participant.utn, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (participant.heightDivision.isNotBlank()) {
                            Text("Height: ${participant.heightDivision}", fontSize = 12.sp, color = Color(0xFF49454F))
                        }
                    }
                }
            }
        }
    }
}
