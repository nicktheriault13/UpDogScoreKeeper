package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FrizgilityScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FrizgilityScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()

        val scope = rememberCoroutineScope()
        val assetLoader = rememberAssetLoader()
        val exporter = rememberFileExporter()

        // When model emits a pending JSON export, prompt user to save it.
        LaunchedEffect(pendingJsonExport) {
            pendingJsonExport?.let { pending ->
                saveJsonFileWithPicker(pending.filename, pending.content)
                screenModel.consumePendingJsonExport()
            }
        }

        var showAddParticipant by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

        var showImportModeDialog by remember { mutableStateOf(false) }
        var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }

        var showTieWarningDialog by remember { mutableStateOf(false) }
        var tieWarningMessage by remember { mutableStateOf("") }

        // File picker for import
        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImportResult = result
                    showImportModeDialog = true
                }
                else -> { /* ignore */ }
            }
        }

        // Audio
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Frizgility") })
        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top row: Header card and Timer
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Header card with stats and score
                        FrizgilityHeaderCard(
                            navigator = navigator,
                            uiState = uiState,
                            screenModel = screenModel,
                            onUndo = screenModel::undo,
                            onSweetSpot = screenModel::toggleSweetSpot,
                            onAllRollers = screenModel::toggleAllRollers,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer only
                        FrizgilityTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartStop = {
                                if (timerRunning) screenModel.stopTimer() else screenModel.startTimer()
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Main game grid and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main grid (center) - keeping button logic
                        FrizgilityGrid(
                            screenModel = screenModel,
                            uiState = uiState,
                            modifier = Modifier.weight(1f)
                        )

                        // Right side: Queue and Team Management
                        Column(
                            modifier = Modifier.weight(0.3f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Queue card showing teams in queue
                            FrizgilityQueueCard(
                                uiState = uiState,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            FrizgilityTeamManagementCard(
                                screenModel = screenModel,
                                assetLoader = assetLoader,
                                exporter = exporter,
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    val template = assetLoader.load("templates/UDC Frizgility Data Entry L1 Div Sort.xlsx")
                                    if (template != null) {
                                        val bytes = screenModel.exportScoresXlsx(template)

                                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                        val stamp = frizgilityTimestamp(now)
                                        exporter.save("Frizgility_Scores_$stamp.xlsx", bytes)

                                        // Check for ties and show warning if needed
                                        val currentState = screenModel.uiState.value
                                        val allParticipants = mutableListOf<FrizgilityParticipantWithResults>()
                                        allParticipants.addAll(currentState.completedParticipants)
                                        val tieWarning = screenModel.checkForTies(allParticipants)
                                        if (tieWarning != null) {
                                            tieWarningMessage = tieWarning
                                            showTieWarningDialog = true
                                        }
                                    }
                                },
                                onLog = {
                                    val content = screenModel.exportLog()
                                    scope.launch {
                                        saveJsonFileWithPicker("Frizgility_Log.txt", content)
                                    }
                                },
                                onResetRound = { showResetRoundDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Bottom row: Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Navigation buttons below the scoring grid
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = screenModel::flipField,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("↕ FLIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Button(
                                onClick = screenModel::previousParticipant,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("◄◄ PREV", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Button(
                                onClick = screenModel::nextParticipant,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("► NEXT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Button(
                                onClick = screenModel::skipParticipant,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("►► SKIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // Empty spacer to align with right column
                        Spacer(modifier = Modifier.weight(0.3f))
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
                                screenModel.importParticipantsFromCsv(import.contents, FrizgilityScreenModel.ImportMode.Add)
                            } else if (import is ImportResult.Xlsx) {
                                screenModel.importParticipantsFromXlsx(import.bytes, FrizgilityScreenModel.ImportMode.Add)
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
                                    screenModel.importParticipantsFromCsv(import.contents, FrizgilityScreenModel.ImportMode.ReplaceAll)
                                } else if (import is ImportResult.Xlsx) {
                                    screenModel.importParticipantsFromXlsx(import.bytes, FrizgilityScreenModel.ImportMode.ReplaceAll)
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

        if (showClearTeamsDialog) {
            AlertDialog(
                onDismissRequest = { showClearTeamsDialog = false },
                title = { Text("Clear All Teams?") },
                text = { Text("Are you sure you want to clear all teams? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.clearParticipants()
                            showClearTeamsDialog = false
                        }
                    ) {
                        Text("Clear", color = Color(0xFFD50000))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearTeamsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showResetRoundDialog) {
            AlertDialog(
                onDismissRequest = { showResetRoundDialog = false },
                title = { Text("Reset Round?") },
                text = { Text("Are you sure you want to reset the current round? All scores will be lost. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.resetGame()
                            showResetRoundDialog = false
                        }
                    ) {
                        Text("Reset", color = Color(0xFFD50000))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetRoundDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showTieWarningDialog) {
            AlertDialog(
                onDismissRequest = { showTieWarningDialog = false },
                title = { Text("Unresolved Tie Warning", color = Color(0xFFFF9100)) },
                text = { Text(tieWarningMessage) },
                confirmButton = {
                    TextButton(
                        onClick = { showTieWarningDialog = false }
                    ) {
                        Text("OK")
                    }
                }
            )
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
fun FrizgilityHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    uiState: FrizgilityUiState,
    screenModel: FrizgilityScreenModel,
    onUndo: () -> Unit,
    onSweetSpot: () -> Unit,
    onAllRollers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Column 1: Title and UNDO button
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameHomeButton(navigator = navigator)
                        Text(
                            text = "Frizgility",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val active = uiState.activeParticipant
                    Text(
                        text = active?.let { "${it.handler} & ${it.dog}" } ?: "No active team",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFD50000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.width(100.dp).height(42.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Column 2: Main stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Obstacles:", fontSize = 14.sp)
                        Text("${uiState.counters.obstacle1 + uiState.counters.obstacle2 + uiState.counters.obstacle3}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Catches:", fontSize = 14.sp)
                        Text("${uiState.counters.catch3to10 + uiState.counters.catch10plus}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Misses:", fontSize = 14.sp)
                        Text("${uiState.counters.miss}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Miss button at bottom
                Button(
                    onClick = screenModel::handleMissClick,
                    enabled = uiState.missClicksInPhase < 3,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF222222),
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFF616161),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("MISS (${uiState.missClicksInPhase}/3)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Column 3: Score and special buttons
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // Score on one line
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Score: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = uiState.scoreBreakdown.totalScore.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sweet Spot button
                    Button(
                        onClick = onSweetSpot,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (uiState.sweetSpotActive) Color(0xFF00C853) else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SWEET SPOT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // All Rollers button
                    Button(
                        onClick = onAllRollers,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (uiState.allRollersEnabled) Color(0xFF00C853) else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FrizgilityGrid(screenModel: FrizgilityScreenModel, uiState: FrizgilityUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp), elevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3 rows, 5 columns each
            // Columns 1-2: 10 Pts buttons (zone 2 catch) - active during catch phase
            // Column 3: 3 Pts buttons (zone 1 catch) - active during catch phase
            // Column 4: Obstacle 1, 2, 3 (top to bottom) - active during obstacle phase
            // Column 5: Fail 1, 2, 3 (top to bottom) - active during obstacle phase

            repeat(3) { rowIndex ->
                val isBonusRow = rowIndex == 1
                val laneNumber = rowIndex + 1 // 1, 2, 3 for top, middle, bottom

                // Define the column order - reverse if field is flipped
                val columnOrder = if (uiState.fieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isBonusRow) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columnOrder.forEach { colIndex ->
                        when (colIndex) {
                            0, 1 -> {
                                // Columns 1-2: 10 Pts buttons (catch zone 2)
                                val locked = 10 in uiState.catchLocks
                                Button(
                                    onClick = {
                                        screenModel.handleCatchClick(10)
                                    },
                                    enabled = uiState.catchPhaseActive && !locked,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (locked) Color(0xFF00C853) else Color(0xFFF500A1),
                                        contentColor = Color.White,
                                        disabledBackgroundColor = Color(0xFF9E9E9E),
                                        disabledContentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Text("10 Pts", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            2 -> {
                                // Column 3: 3 Pts buttons (catch zone 1)
                                val locked = 3 in uiState.catchLocks
                                Button(
                                    onClick = {
                                        screenModel.handleCatchClick(3)
                                    },
                                    enabled = uiState.catchPhaseActive && !locked,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (locked) Color(0xFF00C853) else Color(0xFFF500A1),
                                        contentColor = Color.White,
                                        disabledBackgroundColor = Color(0xFF9E9E9E),
                                        disabledContentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Text("3 Pts", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            3 -> {
                                // Column 4: Obstacle 1, 2, 3
                                val locked = laneNumber in uiState.laneLocks
                                Button(
                                    onClick = {
                                        screenModel.handleObstacleClick(laneNumber)
                                    },
                                    enabled = uiState.obstaclePhaseActive && !locked,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (locked) Color(0xFF00C853) else Color(0xFF2196F3),
                                        contentColor = Color.White,
                                        disabledBackgroundColor = Color(0xFF9E9E9E),
                                        disabledContentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Text("Obstacle $laneNumber", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            4 -> {
                                // Column 5: Fail 1, 2, 3
                                val locked = laneNumber in uiState.laneLocks
                                Button(
                                    onClick = {
                                        screenModel.handleFailClick(laneNumber)
                                    },
                                    enabled = uiState.obstaclePhaseActive && !locked,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFFD50000),
                                        contentColor = Color.White,
                                        disabledBackgroundColor = Color(0xFF9E9E9E),
                                        disabledContentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Text("Fail $laneNumber", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/*
// Original Frizgility button logic (commented out):
//
// Obstacle Section - 3 lanes with Obs and Fail buttons
// (1..3).forEach { lane ->
//     val locked = lane in uiState.laneLocks
//     Button onClick = { screenModel.handleObstacleClick(lane) }
//     Button onClick = { screenModel.handleFailClick(lane) }
// }
//
// Catch Section - Zone 1 (3), Zone 2 (10), and Miss buttons
// Button onClick = { screenModel.handleCatchClick(3) }
// Button onClick = { screenModel.handleCatchClick(10) }
// Button onClick = { screenModel.handleMissClick() }
*/


@Composable
fun FrizgilityTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: TIMER and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (!timerRunning) onStartStop() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶", fontSize = 16.sp)
                        Text("TIMER", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = { if (timerRunning) onStartStop() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏸", fontSize = 16.sp)
                        Text("PAUSE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Bottom row: EDIT and RESET buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { /* Edit */ },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✎", fontSize = 16.sp)
                        Text("EDIT", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = { /* Reset */ },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("↻", fontSize = 16.sp)
                        Text("RESET", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Time Remaining display
            Text(
                "Time Remaining: ${timeLeft}s",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun FrizgilityQueueCard(uiState: FrizgilityUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Queue",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Display teams in queue - scrollable list that fills available space
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.queue.isEmpty()) {
                    item {
                        Text(
                            "No teams in queue",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(count = uiState.queue.size) { index ->
                        val participant = uiState.queue[index]
                        val isCurrentTeam = index == 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = if (isCurrentTeam) Color(0xFFE3F2FD) else Color.White,
                            elevation = if (isCurrentTeam) 2.dp else 0.dp,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCurrentTeam) "▶ ${participant.handler} & ${participant.dog}"
                                           else "${participant.handler} & ${participant.dog}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrentTeam) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentTeam) Color(0xFF1976D2) else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrizgilityTeamManagementCard(
    screenModel: FrizgilityScreenModel,
    assetLoader: AssetLoader,
    exporter: FileExporter,
    onClearTeams: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onExport: () -> Unit,
    onLog: () -> Unit,
    onResetRound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onClearTeams,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF7B1FA2),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CLEAR TEAMS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF7B1FA2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("IMPORT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onAddTeam,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF7B1FA2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ADD TEAM", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF7B1FA2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("EXPORT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF7B1FA2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("LOG", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onResetRound,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFD50000),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("RESET ROUND", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

private fun frizgilityTimestamp(now: kotlinx.datetime.LocalDateTime): String {
    fun pad2(n: Int) = n.toString().padStart(2, '0')
    return buildString {
        append(now.year)
        append(pad2(now.monthNumber))
        append(pad2(now.dayOfMonth))
        append('_')
        append(pad2(now.hour))
        append(pad2(now.minute))
        append(pad2(now.second))
    }
}

