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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

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
                        ThrowNGoHeaderCard(
                            navigator = navigator,
                            uiState = uiState,
                            onUndo = screenModel::undo,
                            onMiss = screenModel::incrementMiss,
                            onOb = screenModel::incrementOb,
                            onAllRollers = screenModel::toggleAllRollers,
                            onSweetSpot = screenModel::toggleSweetSpot,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer only
                        ThrowNGoTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = audioRemainingSeconds,
                            onStartStop = { if (timerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Main game grid and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main grid (center)
                        ScoringGrid(
                            fieldFlipped = uiState.fieldFlipped,
                            onScore = screenModel::recordThrow,
                            modifier = Modifier.weight(1f)
                        )

                        // Right side: Queue and Team Management
                        Column(
                            modifier = Modifier.weight(0.3f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Queue card showing teams in queue
                            ThrowNGoQueueCard(
                                participants = uiState.queue,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            ThrowNGoTeamManagementCard(
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    val template = assetLoader.load("templates/UDC Throw N Go Data Entry L1 or L2 Div Sort.xlsx")
                                    if (template != null) {
                                        val bytes = screenModel.exportScoresXlsx(template)
                                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                        val stamp = throwNGoTimestamp(now)
                                        exporter.save("ThrowNGo_Scores_$stamp.xlsx", bytes)
                                    }
                                },
                                onLog = {
                                    val content = screenModel.exportLog()
                                    scope.launch {
                                        saveJsonFileWithPicker("ThrowNGo_Log.txt", content)
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
                            // Call reset round function when implemented
                            // screenModel.resetRound()
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
private fun ThrowNGoHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    uiState: ThrowNGoUiState,
    onUndo: () -> Unit,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    onAllRollers: () -> Unit,
    onSweetSpot: () -> Unit,
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
                            text = "Throw N Go",
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
                    Text("Catches:", fontSize = 14.sp)
                    Text("${uiState.scoreState.catches}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Bonus Catches:", fontSize = 14.sp)
                    Text("${uiState.scoreState.bonusCatches}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // MISS and OB buttons in same row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Miss button (red background)
                    Button(
                        onClick = onMiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MISS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${uiState.scoreState.misses}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // OB button
                    Button(
                        onClick = onOb,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFFF8A50),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${uiState.scoreState.ob}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
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
                            text = uiState.scoreState.score.toString(),
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
                            backgroundColor = if (uiState.scoreState.sweetSpotActive) Color(0xFF00C853) else Color(0xFF9E9E9E),
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
                            backgroundColor = if (uiState.scoreState.allRollersActive) Color(0xFF00C853) else Color(0xFF9E9E9E),
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
private fun ThrowNGoTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            // Time display - large and prominent
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
private fun ThrowNGoQueueCard(
    participants: List<ThrowNGoParticipant>,
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
            // Shows at least 5 teams without scrolling when space allows
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (participants.isEmpty()) {
                    item {
                        Text(
                            "No teams in queue",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                 else {
                    items(count = participants.size) { index ->
                        val participant = participants[index]
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
private fun ThrowNGoTeamManagementCard(
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
    onUndo: () -> Unit,
    onSweetSpot: () -> Unit,
    sweetSpotActive: Boolean,
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

            Button(onClick = onUndo) { Text("Undo") }

            Button(
                onClick = onSweetSpot,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (sweetSpotActive) Color(0xFF00C853) else Color(0xFFFF9100))
            ) {
                Text("Sweet Spot", color = Color.White)
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
private fun ScoringGrid(fieldFlipped: Boolean, onScore: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(18.dp), elevation = 8.dp, modifier = modifier.fillMaxSize()) {
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
                // Reverse column order when field is flipped
                val displayValues = if (fieldFlipped) rowValues.reversed() else rowValues

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isBonusRow) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayValues.forEach { value ->
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

private fun throwNGoTimestamp(now: kotlinx.datetime.LocalDateTime): String {
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
