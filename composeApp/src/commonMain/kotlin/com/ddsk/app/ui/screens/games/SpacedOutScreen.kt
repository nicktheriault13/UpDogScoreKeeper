package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
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

object SpacedOutScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val myModel = rememberScreenModel { SpacedOutScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) { myModel.initPersistence(dataStore) }

        val scoreState = myModel.score
        val score by scoreState.collectAsState()
        val spacedOutCount by myModel.spacedOutCount.collectAsState()
        val misses by myModel.misses.collectAsState()
        val ob by myModel.ob.collectAsState()
        val zonesCaught by myModel.zonesCaught.collectAsState()
        val clickedZones by myModel.clickedZonesInRound.collectAsState()
        val sweetSpotBonusOn by myModel.sweetSpotBonusOn.collectAsState()
        val allRollersOn by myModel.allRollersOn.collectAsState()
        val fieldFlipped by myModel.fieldFlipped.collectAsState()
        val activeParticipant by myModel.activeParticipant.collectAsState()
        val queue by myModel.participantQueue.collectAsState()
        val timeLeft by myModel.timeLeft.collectAsState()
        val timerRunning by myModel.timerRunning.collectAsState()
        val pendingJsonExport by myModel.pendingJsonExport.collectAsState()

        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            myModel.consumePendingJsonExport()
        }

        val scope = rememberCoroutineScope()
        var showAddParticipant by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

        var handlerInput by remember { mutableStateOf("") }
        var dogInput by remember { mutableStateOf("") }
        var utnInput by remember { mutableStateOf("") }

        var exportBuffer by remember { mutableStateOf<String?>(null) }
        var logBuffer by remember { mutableStateOf<String?>(null) }

        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> myModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> myModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }

        val exporter = rememberFileExporter()
        val assetLoader = rememberAssetLoader()
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Spaced Out") })

        LaunchedEffect(timerRunning) { if (timerRunning) audioPlayer.play() else audioPlayer.stop() }

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
                        SpacedOutHeaderCard(
                            navigator = navigator,
                            activeParticipant = activeParticipant,
                            score = score,
                            spacedOutCount = spacedOutCount,
                            zonesCaught = zonesCaught,
                            misses = misses,
                            ob = ob,
                            onMiss = myModel::incrementMisses,
                            onOb = myModel::incrementOb,
                            sweetSpotBonusOn = sweetSpotBonusOn,
                            allRollersOn = allRollersOn,
                            onSweetSpotToggle = myModel::toggleSweetSpotBonus,
                            onAllRollersToggle = myModel::toggleAllRollers,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer card
                        SpacedOutTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartStop = { if (timerRunning) myModel.stopTimer() else myModel.startTimer() },
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
                            fieldFlipped = fieldFlipped,
                            clickedZones = clickedZones,
                            screenModel = myModel,
                            modifier = Modifier.weight(1f)
                        )

                        // Right side: Queue and Team Management
                        Column(
                            modifier = Modifier.weight(0.3f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Queue card showing teams in queue
                            SpacedOutQueueCard(
                                participants = queue,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            SpacedOutTeamManagementCard(
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    val template = assetLoader.load("templates/UDC Spaced Out Data Entry L1 Div Sort.xlsx")
                                    if (template != null) {
                                        val bytes = myModel.exportParticipantsAsXlsx(template)
                                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                        fun pad2(v: Int) = v.toString().padStart(2, '0')
                                        val dateStr = buildString {
                                            append(now.year); append(pad2(now.monthNumber)); append(pad2(now.dayOfMonth)); append("_")
                                            append(pad2(now.hour)); append(pad2(now.minute)); append(pad2(now.second))
                                        }
                                        exporter.save("SpacedOut_Scores_${dateStr}.xlsx", bytes)
                                    } else {
                                        exportBuffer = myModel.exportParticipantsAsCsv()
                                    }
                                },
                                onLog = { logBuffer = myModel.exportLog() },
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
                                onClick = myModel::flipField,
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
                                onClick = myModel::previousParticipant,
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
                                onClick = myModel::nextParticipant,
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
                                onClick = myModel::skipParticipant,
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
                    myModel.addParticipant(handlerInput, dogInput, utnInput)
                    handlerInput = ""; dogInput = ""; utnInput = ""; showAddParticipant = false
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
                            myModel.clearParticipants()
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
                text = { Text("Are you sure you want to reset the round? This will clear the current score and progress.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            myModel.reset()
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

        exportBuffer?.let { payload -> TextPreviewDialog(title = "Export Participants", text = payload, onDismiss = { exportBuffer = null }) }
        logBuffer?.let { payload -> TextPreviewDialog(title = "Run Log", text = payload, onDismiss = { logBuffer = null }) }
    }
}


@Composable
private fun SpacedOutHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    activeParticipant: SpacedOutParticipant?,
    score: Int,
    spacedOutCount: Int,
    zonesCaught: Int,
    misses: Int,
    ob: Int,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    sweetSpotBonusOn: Boolean,
    allRollersOn: Boolean,
    onSweetSpotToggle: () -> Unit,
    onAllRollersToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material.Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Column 1: Home button and active team
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameHomeButton(navigator = navigator)
                    Text(
                        text = "Spaced Out",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = activeParticipant?.displayName() ?: "No active team",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }

            // Column 2: Main stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Spaced Out:", fontSize = 14.sp)
                    Text("$spacedOutCount", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Zones Caught:", fontSize = 14.sp)
                    Text("$zonesCaught", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            Text("$misses", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            Text("$ob", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            text = score.toString(),
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
                    // Sweet Spot Bonus button
                    Button(
                        onClick = onSweetSpotToggle,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (sweetSpotBonusOn) Color(0xFF00C853) else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SWEET SPOT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // All Rollers button
                    Button(
                        onClick = onAllRollersToggle,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (allRollersOn) Color(0xFF00C853) else Color(0xFF9E9E9E),
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
private fun SpacedOutTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material.Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
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
private fun SpacedOutQueueCard(
    participants: List<SpacedOutParticipant>,
    modifier: Modifier = Modifier
) {
    androidx.compose.material.Card(
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
                        androidx.compose.material.Card(
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
                                    text = if (isCurrentTeam) "▶ ${participant.displayName()}"
                                           else participant.displayName(),
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
private fun SpacedOutTeamManagementCard(
    onClearTeams: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onExport: () -> Unit,
    onLog: () -> Unit,
    onResetRound: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material.Card(
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
            TextButton(onClick = onConfirm) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TextPreviewDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().height(220.dp),
                readOnly = true
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ScoringGrid(
    fieldFlipped: Boolean,
    clickedZones: Set<SpacedOutZone>,
    screenModel: SpacedOutScreenModel,
    modifier: Modifier = Modifier
) {
    // Grid: 3 rows x 5 columns. Middle row is double height.
    // Only 4 scoring buttons are shown at fixed coordinates (after applying flip mapping):
    // - Zone1 at (row=2,col=1)
    // - Sweet Spot (zone button) at (row=1,col=2)
    // - Zone2 at (row=0,col=2)
    // - Zone3 at (row=0,col=3)

    androidx.compose.material.Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (visualRow in 0..2) {
                val isBonusRow = visualRow == 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isBonusRow) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (visualCol in 0..4) {
                        // Apply React-style flip mapping: actualRow/Col is reversed when flipped.
                        val actualRow = if (fieldFlipped) 2 - visualRow else visualRow
                        val actualCol = if (fieldFlipped) 4 - visualCol else visualCol

                        val zone: SpacedOutZone? = when {
                            actualRow == 2 && actualCol == 1 -> SpacedOutZone.Zone1
                            actualRow == 1 && actualCol == 2 -> SpacedOutZone.Zone2 // Sweet Spot zone button uses Zone2
                            actualRow == 0 && actualCol == 2 -> SpacedOutZone.Zone3
                            actualRow == 0 && actualCol == 3 -> SpacedOutZone.Zone4
                            else -> null
                        }

                        if (zone != null) {
                            val isClicked = clickedZones.contains(zone)
                            val label = when (zone) {
                                SpacedOutZone.Zone1 -> "zone1"
                                SpacedOutZone.Zone2 -> "Sweet Spot"
                                SpacedOutZone.Zone3 -> "zone2"
                                SpacedOutZone.Zone4 -> "zone3"
                                else -> zone.label
                            }

                            Button(
                                onClick = { screenModel.handleZoneClick(zone) },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (isClicked) Color(0xFF00C853) else Color(0xFFF500A1),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = label,
                                    fontSize = if (zone == SpacedOutZone.Zone2) 16.sp else 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Grey inactive button for empty spaces
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
                                Text(
                                    text = "",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

