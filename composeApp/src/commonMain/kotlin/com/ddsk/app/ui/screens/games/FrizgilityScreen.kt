package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

object FrizgilityScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FrizgilityScreenModel() }
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val logEntries by screenModel.logEntries.collectAsState()

        var showLog by rememberSaveable { mutableStateOf(false) }
        var showAddTeam by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        var showClearConfirm by rememberSaveable { mutableStateOf(false) }
        var exportPreview by rememberSaveable { mutableStateOf<String?>(null) }
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

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Frizgility") })

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF1F1F1))) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Sidebar
                FrizgilitySidebar(
                    collapsed = uiState.sidebarCollapsed,
                    participants = uiState.participants,
                    activeParticipantIndex = uiState.activeParticipantIndex,
                    onToggleCollapse = screenModel::toggleSidebar,
                    onLog = { showLog = true },
                    onExportSnapshot = { exportPreview = screenModel.exportParticipantsSnapshot() },
                    onImport = { filePicker.launch() },
                    onAddTeam = { showAddTeam = true },
                    onHelp = { showHelp = true },
                    onResetScore = screenModel::resetGame,
                    onClearParticipants = { showClearConfirm = true }
                )

                // Main Content
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Header Status
                    FrizgilityHeader(
                        activeParticipant = uiState.activeParticipant,
                        breakdown = uiState.scoreBreakdown,
                        timeLeft = timeLeft,
                        timerRunning = timerRunning,
                        onTimerToggle = { if (timerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                        onTimerReset = screenModel::resetTimer
                    )

                    // Game Board
                    FrizgilityBoard(
                        uiState = uiState,
                        onObstacle = { lane -> screenModel.handleObstacleClick(lane) },
                        onFail = { lane -> screenModel.handleFailClick(lane) },
                        onCatch = { points -> screenModel.handleCatchClick(points) },
                        onMiss = screenModel::handleMissClick,
                        onSweetSpot = screenModel::toggleSweetSpot,
                        onAllRollers = { screenModel.toggleAllRollers() },
                        modifier = Modifier.weight(1f)
                    )

                    // Controls
                    FrizgilityControls(
                        onNext = screenModel::nextParticipant,
                        onSkip = screenModel::skipParticipant,
                        onUndo = screenModel::undo
                    )
                }
            }

            if (showAddTeam) {
                AddTeamDialog(
                    onAdd = { h, d, u ->
                        screenModel.addParticipant(h, d, u)
                        showAddTeam = false
                    },
                    onDismiss = { showAddTeam = false }
                )
            }

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    title = { Text("Clear All Teams?") },
                    text = { Text("This removes every queued team and cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                screenModel.clearParticipants()
                                showClearConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB3261E))
                        ) { Text("Clear", color = Color.White) }
                    },
                    dismissButton = {
                        Button(onClick = { showClearConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            if (showHelp) {
                AlertDialog(
                    onDismissRequest = { showHelp = false },
                    title = { Text("Quick Guide") },
                    text = { Text(HELP_TEXT) },
                    confirmButton = {
                        Button(onClick = { showHelp = false }) { Text("Close") }
                    }
                )
            }

            if (showLog) {
                AlertDialog(
                    onDismissRequest = { showLog = false },
                    title = { Text("Run Log") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            itemsIndexed(logEntries) { _, entry -> Text(entry, fontSize = 12.sp) }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showLog = false }) { Text("Close") }
                    }
                )
            }

            if (exportPreview != null) {
                AlertDialog(
                    onDismissRequest = { exportPreview = null },
                    title = { Text("Export Preview") },
                    text = { OutlinedTextField(value = exportPreview ?: "", onValueChange = {}, readOnly = true) },
                    confirmButton = {
                        Button(onClick = { exportPreview = null }) { Text("Close") }
                    }
                )
            }
        }
    }
}

@Composable
private fun FrizgilitySidebar(
    collapsed: Boolean,
    participants: List<FrizgilityParticipant>,
    activeParticipantIndex: Int,
    onToggleCollapse: () -> Unit,
    onLog: () -> Unit,
    onExportSnapshot: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit,
    onResetScore: () -> Unit,
    onClearParticipants: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (collapsed) 72.dp else 240.dp)
            .fillMaxHeight()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF6750A4).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onToggleCollapse, modifier = Modifier.fillMaxWidth()) {
            Text(if (collapsed) ">>" else "Hide")
        }

        if (!collapsed) {
            SidebarButton(text = "Add Team", color = Color(0xFF2979FF), onClick = onAddTeam)
            SidebarButton(text = "Import", color = Color(0xFF2979FF), onClick = onImport)
            SidebarButton(text = "Export", color = Color(0xFF2979FF), onClick = onExportSnapshot)
            SidebarButton(text = "Help", color = Color(0xFF6750A4), onClick = onHelp)
            SidebarButton(text = "Log", color = Color(0xFFEADDFF), onClick = onLog)
            SidebarButton(text = "Reset Game", color = Color(0xFFB3261E), onClick = onResetScore)
            SidebarButton(text = "Clear Queue", color = Color(0xFFB3261E), onClick = onClearParticipants)

            Spacer(Modifier.height(16.dp))
            Text("Queue (${participants.size})", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f).border(1.dp, Color.LightGray).padding(4.dp)) {
                itemsIndexed(participants) { index, team ->
                    val isActive = index == activeParticipantIndex
                    Text(
                        text = "${team.handler} & ${team.dog}",
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color(0xFF6750A4) else Color.Black,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = if (color == Color(0xFFEADDFF)) Color.Black else Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun FrizgilityHeader(
    activeParticipant: FrizgilityParticipant?,
    breakdown: ScoreBreakdown,
    timeLeft: Int,
    timerRunning: Boolean,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Participant Info
        Column(modifier = Modifier.weight(1f)) {
            Text("Active Team", fontSize = 12.sp, color = Color.Gray)
            Text(
                text = activeParticipant?.let { "${it.handler} & ${it.dog}" } ?: "None",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text("Total Score: ${breakdown.totalScore}", fontSize = 18.sp, color = Color(0xFF2979FF))
        }

        // Timer
        Column(modifier = Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Time Remaining", fontSize = 12.sp, color = Color.Gray)
            Text("${timeLeft}s", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = if (timeLeft < 10) Color(0xFFB3261E) else Color.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTimerToggle, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    Text(if (timerRunning) "Stop" else "Start", fontSize = 12.sp)
                }
                Button(onClick = onTimerReset, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FrizgilityBoard(
    uiState: FrizgilityUiState,
    onObstacle: (Int) -> Unit,
    onFail: (Int) -> Unit,
    onCatch: (Int) -> Unit,
    onMiss: () -> Unit,
    onSweetSpot: () -> Unit,
    onAllRollers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Lane 1
            LaneColumn(
                header = "Obstacle 1",
                obstacleCount = uiState.counters.obstacle1,
                failCount = uiState.counters.fail1,
                isLocked = 1 in uiState.laneLocks,
                enabled = uiState.obstaclePhaseActive,
                onObstacle = { onObstacle(1) },
                onFail = { onFail(1) },
                modifier = Modifier.weight(1f)
            )
            // Lane 2
            LaneColumn(
                header = "Obstacle 2",
                obstacleCount = uiState.counters.obstacle2,
                failCount = uiState.counters.fail2,
                isLocked = 2 in uiState.laneLocks,
                enabled = uiState.obstaclePhaseActive,
                onObstacle = { onObstacle(2) },
                onFail = { onFail(2) },
                modifier = Modifier.weight(1f)
            )
            // Lane 3
            LaneColumn(
                header = "Obstacle 3",
                obstacleCount = uiState.counters.obstacle3,
                failCount = uiState.counters.fail3,
                isLocked = 3 in uiState.laneLocks,
                enabled = uiState.obstaclePhaseActive,
                onObstacle = { onObstacle(3) },
                onFail = { onFail(3) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "3-10 Catch",
                subLabel = "${uiState.counters.catch3to10}",
                color = Color(0xFF00C853),
                enabled = uiState.catchPhaseActive && 3 !in uiState.catchLocks,
                onClick = { onCatch(3) },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "10+ Catch",
                subLabel = "${uiState.counters.catch10plus}",
                color = Color(0xFF00B8D4),
                enabled = uiState.catchPhaseActive && 10 !in uiState.catchLocks,
                onClick = { onCatch(10) },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "Miss",
                subLabel = "${uiState.counters.miss} (Phase: ${uiState.missClicksInPhase})",
                color = Color(0xFFB3261E),
                enabled = true,
                onClick = onMiss,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSweetSpot,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (uiState.sweetSpotActive) Color(0xFFF500A1) else Color.LightGray),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { Text("Sweet Spot (${if (uiState.sweetSpotActive) "ON" else "OFF"})", color = Color.White) }

            Button(
                onClick = onAllRollers,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (uiState.allRollersEnabled) Color(0xFFF500A1) else Color.LightGray),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { Text("All Rollers (${if (uiState.allRollersEnabled) "ON" else "OFF"})", color = Color.White) }
        }
    }
}

@Composable
private fun LaneColumn(
    header: String,
    obstacleCount: Int,
    failCount: Int,
    isLocked: Boolean,
    enabled: Boolean,
    onObstacle: () -> Unit,
    onFail: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxHeight().border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(header, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onObstacle,
            enabled = enabled && !isLocked,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2979FF)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Obstacle")
                Text(obstacleCount.toString(), fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onFail,
            enabled = enabled && !isLocked,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF9100)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Refusal")
                Text(failCount.toString(), fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    subLabel: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(subLabel)
        }
    }
}

@Composable
private fun FrizgilityControls(onNext: () -> Unit, onSkip: () -> Unit, onUndo: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("Undo") }
        Button(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next Participant") }
    }
}

@Composable
private fun AddTeamDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var handler by rememberSaveable { mutableStateOf("") }
    var dog by rememberSaveable { mutableStateOf("") }
    var utn by rememberSaveable { mutableStateOf("") }

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
            Button(onClick = { onAdd(handler, dog, utn) }) { Text("Add") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private const val HELP_TEXT = """
Frizgility Rules:
1. Complete 3 obstacles (mini-field) to unlock catch phase.
2. In catch phase, perform one 3-10 yard catch OR one 10+ yard catch.
3. Catch locks out that distance for the rest of the round.
4. If you miss 3 times in obstacle phase -> round over.
5. If you miss once in catch phase -> back to obstacles.
6. Bonus: Sweet Spot awards extra points.
"""
