@file:OptIn(ExperimentalLayoutApi::class)

package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel

object ThrowNGoScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = getScreenModel<ThrowNGoScreenModel>()
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()

        var showAddParticipant by remember { mutableStateOf(false) }
        var handlerInput by remember { mutableStateOf("") }
        var dogInput by remember { mutableStateOf("") }
        var utnInput by remember { mutableStateOf("") }

        var showImport by remember { mutableStateOf(false) }
        var importBuffer by remember { mutableStateOf("") }

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeaderRow(
                    uiState = uiState,
                    timerRunning = timerRunning,
                    timeLeft = timeLeft,
                    onTimerToggle = {
                        if (timerRunning) screenModel.stopTimer() else screenModel.startTimer()
                    },
                    onTimerReset = screenModel::resetTimer,
                    onUndo = screenModel::undo,
                    onSweetSpot = screenModel::toggleSweetSpot,
                    onMiss = screenModel::incrementMiss,
                    onOb = screenModel::incrementOb
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Sidebar(
                        modifier = Modifier.width(180.dp),
                        onImport = { showImport = true },
                        onExport = screenModel::exportParticipantsAsCsv,
                        onExportLog = screenModel::exportLog,
                        onAddTeam = { showAddParticipant = true },
                        onHelp = {},
                        onReset = screenModel::resetRound,
                        onFlip = screenModel::flipField,
                        onPrevious = screenModel::previousParticipant,
                        onNext = screenModel::nextParticipant,
                        onSkip = screenModel::skipParticipant,
                        allRollersActive = uiState.scoreState.allRollersActive,
                        onAllRollersToggle = screenModel::toggleAllRollers
                    )

                    ParticipantList(uiState = uiState, modifier = Modifier.width(220.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        ScoringGrid(
                            uiState = uiState,
                            onScore = screenModel::recordThrow
                        )
                    }
                }

                LogCard(logLines = uiState.logEntries)
            }
        }

        if (showAddParticipant) {
            ParticipantDialog(
                handler = handlerInput,
                dog = dogInput,
                utn = utnInput,
                onHandlerChange = { handlerInput = it },
                onDogChange = { dogInput = it },
                onUtnChange = { utnInput = it },
                onDismiss = { showAddParticipant = false },
                onConfirm = {
                    screenModel.addParticipant(handlerInput, dogInput, utnInput)
                    handlerInput = ""
                    dogInput = ""
                    utnInput = ""
                    showAddParticipant = false
                }
            )
        }

        if (showImport) {
            ImportDialog(
                text = importBuffer,
                onTextChange = { importBuffer = it },
                onDismiss = {
                    showImport = false
                    importBuffer = ""
                },
                onConfirm = {
                    screenModel.importParticipantsFromCsv(importBuffer)
                    showImport = false
                    importBuffer = ""
                }
            )
        }
    }
}

@Composable
private fun HeaderRow(
    uiState: ThrowNGoUiState,
    timerRunning: Boolean,
    timeLeft: Int,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit,
    onUndo: () -> Unit,
    onSweetSpot: () -> Unit,
    onMiss: () -> Unit,
    onOb: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onTimerToggle, colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) Color(0xFFFFB74D) else Color(0xFF2196F3))) {
                Text(if (timerRunning) "${timeLeft}s" else "Timer", color = Color.White)
            }
            Button(onClick = onTimerReset, enabled = !timerRunning) {
                Text("Reset")
            }
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(text = uiState.activeParticipant?.displayName ?: "No team", fontWeight = FontWeight.Bold)
                Text(text = "Score: ${uiState.scoreState.score} • Misses: ${uiState.scoreState.misses} • OB: ${uiState.scoreState.ob}")
            }
            Button(onClick = onUndo) { Text("Undo") }
            Button(onClick = onSweetSpot, colors = ButtonDefaults.buttonColors(backgroundColor = if (uiState.scoreState.sweetSpotActive) Color(0xFF00C853) else Color(0xFFFF9100))) {
                Text("Sweet Spot", color = Color.White)
            }
            Button(onClick = onMiss, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD50000))) {
                Text("Miss", color = Color.White)
            }
            Button(onClick = onOb, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFFB74D))) {
                Text("OB", color = Color(0xFF442800))
            }
        }
    }
}

@Composable
private fun Sidebar(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onExport: () -> String,
    onExportLog: () -> String,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
    onFlip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    allRollersActive: Boolean,
    onAllRollersToggle: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SidebarButton(label = "Import", onClick = onImport)
            SidebarButton(label = "Export") { onExport() }
            SidebarButton(label = "Log") { onExportLog() }
            SidebarButton(label = "Add Team", onClick = onAddTeam)
            SidebarButton(label = "Help", onClick = onHelp)
            SidebarButton(label = "Reset", onClick = onReset)
            SidebarButton(label = "Flip Field", onClick = onFlip)
            SidebarButton(label = "Previous", onClick = onPrevious)
            SidebarButton(label = "Next", onClick = onNext)
            SidebarButton(label = "Skip", onClick = onSkip)
            SidebarButton(label = if (allRollersActive) "All Rollers On" else "All Rollers", onClick = onAllRollersToggle)
        }
    }
}

@Composable
private fun SidebarButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun ParticipantList(uiState: ThrowNGoUiState, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier.heightIn(min = 200.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Remaining (${uiState.queue.size})", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.queue) { participant ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(participant.displayName, fontWeight = FontWeight.SemiBold)
                        Text(participant.utn, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoringGrid(uiState: ThrowNGoUiState, onScore: (Int, Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), elevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                listOf(1, 3, 4, 5, 6),
                listOf(1, 6, 8, 10, 12),
                listOf(1, 3, 4, 5, 6)
            ).forEachIndexed { rowIndex, rowValues ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowValues.forEach { value ->
                        Button(
                            onClick = { onScore(value, rowIndex == 1) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF500A1), contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(if (rowIndex == 1) 110.dp else 80.dp)
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
private fun LogCard(logLines: List<String>) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp).height(140.dp)) {
            Text(text = "Log", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                logLines.asReversed().forEach { line ->
                    Text(text = line, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ParticipantDialog(
    handler: String,
    dog: String,
    utn: String,
    onHandlerChange: (String) -> Unit,
    onDogChange: (String) -> Unit,
    onUtnChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = handler, onValueChange = onHandlerChange, label = { Text("Handler") })
                OutlinedTextField(value = dog, onValueChange = onDogChange, label = { Text("Dog") })
                OutlinedTextField(value = utn, onValueChange = onUtnChange, label = { Text("UTN") })
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = handler.isNotBlank() && dog.isNotBlank() && utn.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ImportDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Participants") },
        text = {
            Column {
                Text(text = "Paste CSV rows (handler,dog,utn)", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.height(160.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
