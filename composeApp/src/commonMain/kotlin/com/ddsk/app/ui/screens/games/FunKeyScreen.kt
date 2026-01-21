package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.*
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Color Palette
private val vPrimary = Color(0xFF2979FF)
private val vPrimaryOn = Color(0xFFFFFFFF)
private val vSuccess = Color(0xFF00C853)
private val vSuccessOn = Color(0xFFFFFFFF)

object FunKeyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FunKeyScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val score by screenModel.score.collectAsState()
        val sweetSpotOn by screenModel.sweetSpotOn.collectAsState()
        val allRollers by screenModel.allRollers.collectAsState()
        val isPurpleEnabled by screenModel.isPurpleEnabled.collectAsState()
        val isBlueEnabled by screenModel.isBlueEnabled.collectAsState()
        val jump1Count by screenModel.jump1Count.collectAsState()
        val jump2Count by screenModel.jump2Count.collectAsState()
        val jump3Count by screenModel.jump3Count.collectAsState()
        val jump2bCount by screenModel.jump2bCount.collectAsState()
        val jump3bCount by screenModel.jump3bCount.collectAsState()
        val tunnelCount by screenModel.tunnelCount.collectAsState()
        val key1Count by screenModel.key1Count.collectAsState()
        val key2Count by screenModel.key2Count.collectAsState()
        val key3Count by screenModel.key3Count.collectAsState()
        val key4Count by screenModel.key4Count.collectAsState()
        val activatedKeys by screenModel.activatedKeys.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantQueue by screenModel.participantQueue.collectAsState()
        val completedParticipants by screenModel.completedParticipants.collectAsState()

        var isTimerRunning by remember { mutableStateOf(false) }
        var showAddDialog by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }
        var showImportChoice by remember { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<ImportResult?>(null) }
        var isFlipped by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Fun Key") })
        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImport = result
                    showImportChoice = true
                }
                else -> Unit
            }
        }
        val fileExporter = rememberFileExporter()
        val assetLoader = rememberAssetLoader()

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audioPlayer.play() else audioPlayer.stop()
        }

        // Handle JSON export when Next is clicked
        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top row: Header card and Timer
                Row(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: Header card with stats and score
                    FunKeyHeaderCard(
                        navigator = navigator,
                        activeParticipant = activeParticipant,
                        score = score,
                        obstaclesCount = jump1Count + jump2Count + jump3Count + jump2bCount + jump3bCount + tunnelCount,
                        catchesCount = key1Count + key2Count + key3Count + key4Count,
                        allRollers = allRollers,
                        onUndo = screenModel::undo,
                        onToggleAllRollers = screenModel::toggleAllRollers,
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    )

                    // Right: Timer card
                    FunKeyTimerCard(
                        timerRunning = isTimerRunning,
                        onStartStop = { isTimerRunning = !isTimerRunning },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                // Middle row: Main game grid and Team Management
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main grid (center)
                    FunKeyFieldCard(
                        isPurpleEnabled = isPurpleEnabled,
                        isBlueEnabled = isBlueEnabled,
                        sweetSpotOn = sweetSpotOn,
                        jump3Count = jump3Count,
                        jump1Count = jump1Count,
                        jump3bCount = jump3bCount,
                        jump2Count = jump2Count,
                        jump2bCount = jump2bCount,
                        tunnelCount = tunnelCount,
                        key1Count = key1Count,
                        key2Count = key2Count,
                        key3Count = key3Count,
                        key4Count = key4Count,
                        activatedKeys = activatedKeys,
                        onJump = { _, zone -> screenModel.handleCatch(FunKeyZoneType.JUMP, 0, zone) },
                        onKey = { _, zone -> screenModel.handleCatch(FunKeyZoneType.KEY, 0, zone) },
                        onSweetSpot = screenModel::toggleSweetSpot,
                        isFlipped = isFlipped,
                        modifier = Modifier.weight(1f)
                    )

                    // Right side: Queue and Team Management
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Queue card showing teams in queue
                        FunKeyQueueCard(
                            participants = participantQueue,
                            modifier = Modifier.weight(1f)
                        )

                        // Team Management section
                        FunKeyTeamManagementCard(
                            onClearTeams = { showClearTeamsDialog = true },
                            onImport = { filePicker.launch() },
                            onAddTeam = { showAddDialog = true },
                            onExport = {
                                scope.launch {
                                    exportToExcel(
                                        assetLoader = assetLoader,
                                        fileExporter = fileExporter,
                                        completed = completedParticipants
                                    )
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
                            onClick = { isFlipped = !isFlipped },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⇄ FLIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Button(
                            onClick = { /* Previous participant logic */ },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("◄ PREV", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Button(
                            onClick = screenModel::nextParticipant,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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

        if (showAddDialog) {
            FunKeyAddParticipantDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    showAddDialog = false
                }
            )
        }

        if (showImportChoice) {
            AlertDialog(
                onDismissRequest = {
                    showImportChoice = false
                    pendingImport = null
                },
                title = { Text("Import participants") },
                text = { Text("Do you want to add these participants to the current list, or replace the current list?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val importRes = pendingImport
                            showImportChoice = false
                            pendingImport = null
                            if (importRes != null) {
                                when (importRes) {
                                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(importRes.contents, FunKeyScreenModel.ImportMode.Add)
                                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(importRes.bytes, FunKeyScreenModel.ImportMode.Add)
                                    else -> Unit
                                }
                            }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material.OutlinedButton(
                            onClick = {
                                val importRes = pendingImport
                                showImportChoice = false
                                pendingImport = null
                                if (importRes != null) {
                                    when (importRes) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(importRes.contents, FunKeyScreenModel.ImportMode.ReplaceAll)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(importRes.bytes, FunKeyScreenModel.ImportMode.ReplaceAll)
                                        else -> Unit
                                    }
                                }
                            }
                        ) { Text("Replace") }
                        androidx.compose.material.TextButton(
                            onClick = {
                                showImportChoice = false
                                pendingImport = null
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
                    Button(
                        onClick = {
                            screenModel.clearParticipants()
                            showClearTeamsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    Button(onClick = { showClearTeamsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showResetRoundDialog) {
            AlertDialog(
                onDismissRequest = { showResetRoundDialog = false },
                title = { Text("Reset Round?") },
                text = { Text("Are you sure you want to reset the round? This will clear the current score and progress.") },
                confirmButton = {
                    Button(
                        onClick = {
                            screenModel.reset()
                            showResetRoundDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    Button(onClick = { showResetRoundDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}


@Composable
private fun FunKeyHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    activeParticipant: FunKeyParticipant?,
    score: Int,
    obstaclesCount: Int,
    catchesCount: Int,
    allRollers: Boolean,
    onUndo: () -> Unit,
    onToggleAllRollers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Column 1: Home button, active team, and Undo button
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameHomeButton(navigator = navigator)
                        Text(
                            text = "Fun Key",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = activeParticipant?.let { "${it.handler} & ${it.dog}" } ?: "No active team",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                // Undo button at bottom of this column
                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF9800),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(38.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Column 2: Counters centered
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Obstacles counter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Obstacles:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )
                    Text(
                        text = obstaclesCount.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2979FF)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Catches counter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Catches:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )
                    Text(
                        text = catchesCount.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00C853)
                    )
                }
            }

            // Column 3: Score and All Rollers button
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Score on one line
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Score: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = score.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // All Rollers button below score
                    Button(
                        onClick = onToggleAllRollers,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (allRollers) vSuccess else Color(0xFF757575),
                            contentColor = if (allRollers) vSuccessOn else Color.White
                        ),
                        modifier = Modifier.height(38.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun FunKeyTimerCard(
    timerRunning: Boolean,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("↻", fontSize = 16.sp)
                        Text("RESET", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FunKeyQueueCard(
    participants: List<FunKeyParticipant>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                if (participants.isEmpty()) {
                    item {
                        Text(
                            "No teams in queue",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(count = participants.size) { index ->
                        val participant = participants[index]
                        val isCurrentTeam = index == 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = if (isCurrentTeam) Color(0xFFE3F2FD) else Color.White,
                            elevation = if (isCurrentTeam) 2.dp else 0.dp,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
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
private fun FunKeyTeamManagementCard(
    onClearTeams: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onExport: () -> Unit,
    onResetRound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("EXPORT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text("RESET ROUND", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FunKeyFieldCard(
    isPurpleEnabled: Boolean,
    isBlueEnabled: Boolean,
    sweetSpotOn: Boolean,
    jump3Count: Int,
    jump1Count: Int,
    jump3bCount: Int,
    jump2Count: Int,
    jump2bCount: Int,
    tunnelCount: Int,
    key1Count: Int,
    key2Count: Int,
    key3Count: Int,
    key4Count: Int,
    activatedKeys: Set<String>,
    onJump: (Int, String) -> Unit,
    onKey: (Int, String) -> Unit,
    onSweetSpot: () -> Unit,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isFlipped) {
                // Normal layout
                // Row 1: Jump3, Key1, Jump1, Key2, Jump3b
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JumpCell("jump_3pts_left", "Jump 3 PTS", jump3Count, isPurpleEnabled) { onJump(3, "JUMP3") }
                    KeyCell("1", 1, key1Count, isBlueEnabled, activatedKeys.contains("KEY1")) { onKey(1, "KEY1") }
                    JumpCell("jump_1pt", "Jump 1 PTS", jump1Count, isPurpleEnabled) { onJump(1, "JUMP1") }
                    KeyCell("2", 2, key2Count, isBlueEnabled, activatedKeys.contains("KEY2")) { onKey(2, "KEY2") }
                    JumpCell("jump_3pts_right", "Jump 3 PTS", jump3bCount, isPurpleEnabled) { onJump(3, "JUMP3B") }
                }

                // Row 2: Empty, Empty, Sweet Spot, Empty, Empty
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GreyCell()
                    GreyCell()
                    SweetSpotCell(sweetSpotOn, onSweetSpot)
                    GreyCell()
                    GreyCell()
                }

                // Row 3: Jump2, Key4, Tunnel, Key3, Jump2b
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JumpCell("jump_2pts_left", "Jump 2 PTS", jump2Count, isPurpleEnabled) { onJump(2, "JUMP2") }
                    KeyCell("4", 4, key4Count, isBlueEnabled, activatedKeys.contains("KEY4")) { onKey(4, "KEY4") }
                    JumpCell("tunnel", "Tunnel 1 PTS", tunnelCount, isPurpleEnabled) { onJump(0, "TUNNEL") }
                    KeyCell("3", 3, key3Count, isBlueEnabled, activatedKeys.contains("KEY3")) { onKey(3, "KEY3") }
                    JumpCell("jump_2pts_right", "Jump 2 PTS", jump2bCount, isPurpleEnabled) { onJump(2, "JUMP2B") }
                }
            } else {
                // Flipped layout - reverse row and column order
                // Row 3 (now Row 1): Jump2b, Key3, Tunnel, Key4, Jump2
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JumpCell("jump_2pts_right", "Jump 2 PTS", jump2bCount, isPurpleEnabled) { onJump(2, "JUMP2B") }
                    KeyCell("3", 3, key3Count, isBlueEnabled, activatedKeys.contains("KEY3")) { onKey(3, "KEY3") }
                    JumpCell("tunnel", "Tunnel 1 PTS", tunnelCount, isPurpleEnabled) { onJump(0, "TUNNEL") }
                    KeyCell("4", 4, key4Count, isBlueEnabled, activatedKeys.contains("KEY4")) { onKey(4, "KEY4") }
                    JumpCell("jump_2pts_left", "Jump 2 PTS", jump2Count, isPurpleEnabled) { onJump(2, "JUMP2") }
                }

                // Row 2 (stays Row 2): Empty, Empty, Sweet Spot, Empty, Empty (reversed)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GreyCell()
                    GreyCell()
                    SweetSpotCell(sweetSpotOn, onSweetSpot)
                    GreyCell()
                    GreyCell()
                }

                // Row 1 (now Row 3): Jump3b, Key2, Jump1, Key1, Jump3
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JumpCell("jump_3pts_right", "Jump 3 PTS", jump3bCount, isPurpleEnabled) { onJump(3, "JUMP3B") }
                    KeyCell("2", 2, key2Count, isBlueEnabled, activatedKeys.contains("KEY2")) { onKey(2, "KEY2") }
                    JumpCell("jump_1pt", "Jump 1 PTS", jump1Count, isPurpleEnabled) { onJump(1, "JUMP1") }
                    KeyCell("1", 1, key1Count, isBlueEnabled, activatedKeys.contains("KEY1")) { onKey(1, "KEY1") }
                    JumpCell("jump_3pts_left", "Jump 3 PTS", jump3Count, isPurpleEnabled) { onJump(3, "JUMP3") }
                }
            }
        }
    }
}

@Composable
private fun RowScope.JumpCell(imageName: String, pointsText: String, count: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (enabled) Color(0xFFF500A1) else Color(0xFFE0E0E0), // Pink default
            contentColor = if (enabled) Color.White else Color(0xFF9E9E9E),
            disabledBackgroundColor = Color(0xFFE0E0E0),
            disabledContentColor = Color(0xFF9E9E9E)
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pointsText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            if (count > 0) {
                Text(
                    text = "[$count]",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = if (enabled) Color.White else Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    label: String,
    points: Int,
    count: Int,
    enabled: Boolean,
    isActivated: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isActivated,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = when {
                isActivated -> vSuccess // Green when activated
                enabled -> Color(0xFFF500A1) // Pink default
                else -> Color(0xFFE0E0E0)
            },
            contentColor = when {
                isActivated -> vSuccessOn
                enabled -> Color.White
                else -> Color(0xFF9E9E9E)
            },
            disabledBackgroundColor = when {
                isActivated -> vSuccess
                else -> Color(0xFFE0E0E0)
            },
            disabledContentColor = when {
                isActivated -> vSuccessOn
                else -> Color(0xFF9E9E9E)
            }
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            )
            Text(
                text = "$points PT",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RowScope.SweetSpotCell(sweetSpotOn: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (sweetSpotOn) vSuccess else vPrimary,
            contentColor = if (sweetSpotOn) vSuccessOn else vPrimaryOn
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text("SWEET\nSPOT", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun RowScope.GreyCell() {
    Button(
        onClick = { /* Inactive */ },
        enabled = false,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFFE0E0E0),
            contentColor = Color(0xFF9E9E9E),
            disabledBackgroundColor = Color(0xFFE0E0E0),
            disabledContentColor = Color(0xFF9E9E9E)
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text("")
    }
}

@Composable
private fun FunKeyAddParticipantDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") }, singleLine = true)
                OutlinedTextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") }, singleLine = true)
                OutlinedTextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (handler.isNotBlank() && dog.isNotBlank() && utn.isNotBlank()) {
                        onAdd(handler.trim(), dog.trim(), utn.trim())
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun exportParticipants(
    exporter: FileExporter,
    activeParticipant: FunKeyParticipant?,
    participantQueue: List<FunKeyParticipant>
) {
    val rows = buildList {
        activeParticipant?.let { add(it) }
        addAll(participantQueue)
    }
    val csv = buildString {
        appendLine("Handler,Dog,UTN")
        rows.forEach { participant ->
            appendLine("${participant.handler},${participant.dog},${participant.utn}")
        }
    }
    exporter.save("FunKeyParticipants.csv", csv.encodeToByteArray())
}


private suspend fun exportToExcel(
    assetLoader: AssetLoader,
    fileExporter: FileExporter,
    completed: List<FunKeyCompletedParticipant>
) {
    val templateBytes = assetLoader.load("templates/UDC FunKey Data Entry Level 2.xlsm")
    if (templateBytes == null || templateBytes.isEmpty()) {
        println("Failed to load FunKey template")
        return
    }

    // Sort participants by score (descending), then by tiebreakers (4pt, 3pt, 2pt, 1pt clicks - all descending)
    val sortedCompleted = completed.sortedWith(
        compareByDescending<FunKeyCompletedParticipant> { it.score }
            .thenByDescending { it.roundData.fourPointClicks }
            .thenByDescending { it.roundData.threePointClicks }
            .thenByDescending { it.roundData.twoPointClicks }
            .thenByDescending { it.roundData.onePointClicks }
    )

    val exportRows = sortedCompleted.map { participant ->
        FunKeyExportParticipant(
            handler = participant.handler,
            dog = participant.dog,
            utn = participant.utn,
            jump3Sum = participant.roundData.jump3Sum,
            jump2Sum = participant.roundData.jump2Sum,
            jump1TunnelSum = participant.roundData.jump1TunnelSum,
            onePointClicks = participant.roundData.onePointClicks,
            twoPointClicks = participant.roundData.twoPointClicks,
            threePointClicks = participant.roundData.threePointClicks,
            fourPointClicks = participant.roundData.fourPointClicks,
            sweetSpot = if (participant.roundData.sweetSpot) "Y" else "N",
            allRollers = if (participant.roundData.allRollers) "Y" else "N"
        )
    }

    val bytes = generateFunKeyXlsm(exportRows, templateBytes)
    if (bytes.isEmpty()) {
        println("Excel generation failed")
        return
    }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun pad(n: Int) = n.toString().padStart(2, '0')
    val stamp = buildString {
        append(now.year)
        append(pad(now.monthNumber))
        append(pad(now.dayOfMonth))
        append('_')
        append(pad(now.hour))
        append(pad(now.minute))
        append(pad(now.second))
    }
    val fileName = "FunKey_Scores_${stamp}.xlsm"

    fileExporter.save(fileName, bytes)
    println("Exported ${exportRows.size} teams to $fileName")
}
