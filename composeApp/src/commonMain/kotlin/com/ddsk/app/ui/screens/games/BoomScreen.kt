package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.games.ui.ButtonPalette
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val boomPink = Color(0xFFF500A1)
private val disabledBackground = Color(0xFFF1F1F1)
private val disabledContent = Color(0xFF222222)

// K2 can be stricter about file-private visibility; expose this within the module so other screens can reuse it safely.
// Explicit types help K2 inference here.
private val scoringBasePalette: Map<BoomScoringButton, ButtonPalette> = mapOf(
    BoomScoringButton.One to ButtonPalette(Color(0xFF2979FF), Color.White),
    BoomScoringButton.TwoA to ButtonPalette(Color(0xFF00B8D4), Color.White),
    BoomScoringButton.TwoB to ButtonPalette(Color(0xFF00B8D4), Color.White),
    BoomScoringButton.Five to ButtonPalette(Color(0xFFF500A1), Color.White),
    BoomScoringButton.Ten to ButtonPalette(Color(0xFFFFB74D), Color(0xFF442800)),
    BoomScoringButton.Twenty to ButtonPalette(Color(0xFF2979FF), Color.White),
    BoomScoringButton.TwentyFive to ButtonPalette(Color(0xFFF500A1), Color.White),
    BoomScoringButton.ThirtyFive to ButtonPalette(Color(0xFFD50000), Color.White)
)

private fun scoringPaletteFor(
    button: BoomScoringButton,
    clicked: Boolean,
    enabled: Boolean
): ButtonPalette {
    return when {
        clicked -> ButtonPalette(successGreen, Color.White)
        enabled -> scoringBasePalette[button] ?: ButtonPalette(Color(0xFF2979FF), Color.White)
        else -> ButtonPalette(disabledBackground, disabledContent)
    }
}

object BoomScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { BoomScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val dialogState = remember { mutableStateOf<BoomDialogState>(BoomDialogState.None) }
        val activeDialog by dialogState
        var csvBuffer by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }
        var showImportModeDialog by remember { mutableStateOf(false) }
        var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()

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

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Boom") })
        val currentAudioTime by audioPlayer.currentTime.collectAsState()
        val audioRemainingSeconds = remember(audioPlayer.duration, currentAudioTime) {
            ((audioPlayer.duration - currentAudioTime) / 1000).coerceAtLeast(0)
        }

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        // Auto-stop timer when audio finishes
        LaunchedEffect(audioPlayer.isPlaying, timerRunning) {
            if (!audioPlayer.isPlaying && timerRunning) {
                screenModel.stopTimer()
            }
        }

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top row: Header card and Timer/Queue
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Header card with stats and score
                        BoomHeaderCard(
                            navigator = navigator,
                            uiState = uiState,
                            onUndo = screenModel::undo,
                            onToggleAllRollers = screenModel::toggleAllRollers,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer only
                        BoomTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = audioRemainingSeconds,
                            onStartStop = {
                                if (timerRunning) screenModel.stopTimer() else screenModel.startTimer(duration = 60)
                            },
                            onReset = screenModel::resetTimer,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Main game grid and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main grid (center)
                        BoomGrid(
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
                            BoomQueueCard(
                                uiState = uiState,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            BoomTeamManagementCard(
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    csvBuffer = screenModel.exportParticipantsAsCsv()
                                    dialogState.value = BoomDialogState.Export
                                },
                                onLog = {
                                    val content = screenModel.exportLog()
                                    scope.launch {
                                        saveJsonFileWithPicker("Boom_Log.txt", content)
                                    }
                                },
                                onResetRound = { showResetRoundDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Bottom row: Navigation buttons aligned below the grid
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
                                onClick = screenModel::toggleFieldOrientation,
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
                                enabled = uiState.completedParticipants.isNotEmpty(),
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

        if (activeDialog is BoomDialogState.Export) {
            CsvExportDialog(
                csvText = csvBuffer,
                onDismiss = { dialogState.value = BoomDialogState.None }
            )
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
                                screenModel.importParticipantsFromCsv(import.contents, addToExisting = true)
                            } else if (import is ImportResult.Xlsx) {
                                screenModel.importParticipantsFromXlsx(import.bytes, addToExisting = true)
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
                                    screenModel.importParticipantsFromCsv(import.contents, addToExisting = false)
                                } else if (import is ImportResult.Xlsx) {
                                    screenModel.importParticipantsFromXlsx(import.bytes, addToExisting = false)
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
    }
}

@Composable
private fun BoomHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    uiState: BoomUiState,
    onUndo: () -> Unit,
    onToggleAllRollers: () -> Unit,
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
                            text = "Boom",
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Throws:", fontSize = 14.sp)
                    Text("${uiState.scoreBreakdown.throwsCompleted}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Duds:", fontSize = 14.sp)
                    Text("${uiState.scoreBreakdown.duds}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Sweet Spots:", fontSize = 14.sp)
                    Text("${uiState.scoreBreakdown.sweetSpotAwards}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Last Throw:", fontSize = 14.sp)
                    Text("${uiState.scoreBreakdown.lastThrowPoints} pts", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Column 3: Score and All Rollers button
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

                // All Rollers button
                Button(
                    onClick = onToggleAllRollers,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (uiState.allRollersActive) Color(0xFF00C853) else Color(0xFF9E9E9E),
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

@Composable
private fun BoomTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer and Pause buttons
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

            // Time Remaining display - large and prominent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (timerRunning) "${timeLeft}s" else "Ready",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timerRunning) Color(0xFF00BCD4) else Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                if (timerRunning) {
                    Text(
                        text = "Time Remaining",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoomQueueCard(
    uiState: BoomUiState,
    modifier: Modifier = Modifier
) {
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
                    items(uiState.queue) { participant ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color.White,
                            elevation = 0.dp,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${participant.handler} & ${participant.dog}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.Black
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
private fun BoomTeamManagementCard(
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

@Composable
private fun BoomGrid(
    screenModel: BoomScreenModel,
    uiState: BoomUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 16.dp
            val interCellSpacing = 12.dp
            val gridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val widthLimitedCell = ((gridWidth - interCellSpacing * 4) / 5).coerceAtLeast(40.dp)
            val hasFiniteHeight = maxHeight != Dp.Unspecified && maxHeight != Dp.Infinity && maxHeight > 0.dp
            val heightLimitedCell = if (hasFiniteHeight) {
                val totalSpacing = interCellSpacing * 2
                (((maxHeight - totalSpacing) / 3.8f).coerceAtLeast(36.dp))
            } else {
                widthLimitedCell
            }
            val cellSize = minOf(widthLimitedCell, heightLimitedCell)
            val rowOrder = if (uiState.isFieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val columnOrder = if (uiState.isFieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(interCellSpacing)
            ) {
                rowOrder.forEach { rowIndex ->
                    val rowHeight = if (rowIndex == 1) cellSize * 1.8f else cellSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(interCellSpacing)
                    ) {
                        columnOrder.forEach { colIndex ->
                            when (val cell = gridCellContent(rowIndex, colIndex)) {
                                is GridCellContent.Score -> {
                                    ScoringButtonCell(
                                        button = cell.button,
                                        uiState = uiState,
                                        onClick = screenModel::handleScoringButtonClick
                                    )
                                }
                                GridCellContent.Dud -> {
                                    BoomActionButton(
                                        label = "Dud",
                                        color = warningOrange,
                                        onClick = screenModel::handleDud
                                    )
                                }
                                GridCellContent.Boom -> {
                                    BoomActionButton(
                                        label = "Boom!",
                                        color = boomPink,
                                        onClick = screenModel::handleBoom
                                    )
                                }
                                GridCellContent.SweetSpot -> {
                                    SweetSpotCell(uiState = uiState, onToggle = screenModel::toggleSweetSpot)
                                }
                                GridCellContent.Empty -> {
                                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ScoringButtonCell(
    button: BoomScoringButton,
    uiState: BoomUiState,
    onClick: (BoomScoringButton) -> Unit
) {
    val clicked = button.id in uiState.buttonState.clickedButtons
    val enabled = button.id in uiState.buttonState.enabledButtons && !clicked
    val palette = scoringPaletteFor(button, clicked, enabled)
    val colors = ButtonDefaults.buttonColors(
        backgroundColor = palette.background,
        contentColor = palette.content,
        disabledBackgroundColor = palette.background,
        disabledContentColor = palette.content
    )
    Button(
        onClick = { onClick(button) },
        enabled = enabled,
        colors = colors,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = button.label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun RowScope.BoomActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = Color.White),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.SweetSpotCell(uiState: BoomUiState, onToggle: () -> Unit) {
    val isActive = uiState.sweetSpotActive
    Button(
        onClick = onToggle,
        colors = if (isActive) ButtonDefaults.buttonColors(backgroundColor = successGreen, contentColor = Color.White) else ButtonDefaults.buttonColors(),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = if (isActive) "Sweet Spot\nON" else "Sweet Spot", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun CsvExportDialog(csvText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Participants") },
        text = {
            OutlinedTextField(
                value = csvText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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

private sealed interface GridCellContent {
    data class Score(val button: BoomScoringButton) : GridCellContent
    object Dud : GridCellContent
    object Boom : GridCellContent
    object SweetSpot : GridCellContent
    object Empty : GridCellContent
}

private fun gridCellContent(row: Int, col: Int): GridCellContent = when {
    row == 0 && col == 0 -> GridCellContent.Score(BoomScoringButton.One)
    row == 0 && col == 1 -> GridCellContent.Score(BoomScoringButton.TwoB)
    row == 0 && col == 2 -> GridCellContent.Score(BoomScoringButton.Ten)
    row == 0 && col == 3 -> GridCellContent.Score(BoomScoringButton.TwentyFive)
    row == 1 && col == 0 -> GridCellContent.Dud
    row == 1 && col == 1 -> GridCellContent.Boom
    row == 1 && col == 2 -> GridCellContent.SweetSpot
    row == 1 && col == 4 -> GridCellContent.Score(BoomScoringButton.ThirtyFive)
    row == 2 && col == 1 -> GridCellContent.Score(BoomScoringButton.TwoA)
    row == 2 && col == 2 -> GridCellContent.Score(BoomScoringButton.Five)
    row == 2 && col == 3 -> GridCellContent.Score(BoomScoringButton.Twenty)
    else -> GridCellContent.Empty
}

private sealed interface BoomDialogState {
    data object None : BoomDialogState
    data object Export : BoomDialogState
}
