package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.persistence.*
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.theme.Palette
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import com.ddsk.app.media.rememberAudioPlayer
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FireballScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FireballScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val totalScore by screenModel.totalScore.collectAsState()
        val currentBoardScore by screenModel.currentBoardScore.collectAsState()
        val currentBoardRegularPoints by screenModel.currentBoardRegularPoints.collectAsState()
        val currentBoardFireballPoints by screenModel.currentBoardFireballPoints.collectAsState()
        val isFieldFlipped by screenModel.isFieldFlipped.collectAsState()
        val timerSecondsRemaining by screenModel.timerSecondsRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantsQueue by screenModel.participantsQueue.collectAsState()
        val allRollersActive by screenModel.allRollersActive.collectAsState()
        val isFireballActive by screenModel.isFireballActive.collectAsState()
        val sweetSpotBonusAwarded by screenModel.sweetSpotBonusAwarded.collectAsState()
        val completedParticipants by screenModel.completedParticipants.collectAsState()

        val remainingParticipants = participantsQueue.filterNot { participant ->
            val hasScoring = participant.totalPoints > 0
                    || participant.nonFireballPoints > 0
                    || participant.fireballPoints > 0
                    || participant.completedBoards > 0
            hasScoring
        }

        var showClearParticipantsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }
        var showAddTeamDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val fileExporter = rememberFileExporter()
        val assetLoader = rememberAssetLoader()

        // Track when to select a new random audio file (increments each time timer starts)
        var audioSelectionKey by remember { mutableStateOf(0) }

        // Audio player for timer - randomly selects one of 6 fireball timer audio files
        // The key ensures a new random file is selected each time the timer starts
       val currentAudioFileName = remember(audioSelectionKey) { getTimerAssetForGame("Fire Ball") }
        val audioPlayer = rememberAudioPlayer(currentAudioFileName)

        // Extract just the filename from the full path for display
        val audioDisplayName = remember(currentAudioFileName) {
            currentAudioFileName.substringAfterLast("/").substringBeforeLast(".")
        }

        // Track audio player time
        val currentAudioTime by audioPlayer.currentTime.collectAsState()
        val audioRemainingSeconds = remember(audioPlayer.duration, currentAudioTime) {
            (audioPlayer.duration - currentAudioTime) / 1000
        }

        // Play/stop audio when timer state changes
        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        // Hold the latest file import payload until the user chooses Add vs Replace.
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

        // Import choice dialog
        if (showImportChoiceDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportChoiceDialog = false
                    pendingImport = null
                },
                title = { Text("Import participants") },
                text = { Text("Do you want to add the new participants to the existing list, or delete all existing participants and replace them?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val import = pendingImport
                            showImportChoiceDialog = false
                            pendingImport = null
                            if (import != null) {
                                scope.launch {
                                    when (import) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, FireballScreenModel.ImportMode.Add)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, FireballScreenModel.ImportMode.Add)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val import = pendingImport
                                showImportChoiceDialog = false
                                pendingImport = null
                                if (import != null) {
                                    scope.launch {
                                        when (import) {
                                            is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, FireballScreenModel.ImportMode.ReplaceAll)
                                            is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, FireballScreenModel.ImportMode.ReplaceAll)
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        ) { Text("Replace") }
                        TextButton(
                            onClick = {
                                showImportChoiceDialog = false
                                pendingImport = null
                            }
                        ) { Text("Cancel") }
                    }
                }
            )
        }

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
                    FireballHeaderCard(
                        navigator = navigator,
                        activeParticipant = activeParticipant,
                        totalScore = totalScore,
                        boardScore = currentBoardScore,
                        onAllRollers = screenModel::toggleAllRollers,
                        allRollersActive = allRollersActive,
                        onClearBoard = screenModel::clearBoard,
                        onToggleFireball = screenModel::toggleFireball,
                        isFireballActive = isFireballActive,
                        onToggleSweetSpot = screenModel::toggleManualSweetSpot,
                        sweetSpotActive = sweetSpotBonusAwarded,
                        regularPoints = currentBoardRegularPoints,
                        fireballPoints = currentBoardFireballPoints,
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    )

                    // Right: Timer card
                    FireballTimerCard(
                        timerRunning = isTimerRunning,
                        secondsRemaining = audioRemainingSeconds,
                        audioFileName = audioDisplayName,
                        onStartStop = {
                            if (isTimerRunning) {
                                screenModel.stopRoundTimer()
                            } else {
                                // Increment audioSelectionKey to select a new random audio file
                                audioSelectionKey++
                                screenModel.startRoundTimer(64)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                // Middle row: Main game grid and Team Management
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main grid (center)
                    FireballScoringGrid(
                        screenModel = screenModel,
                        isFieldFlipped = isFieldFlipped,
                        isFireballActive = isFireballActive,
                        sweetSpotActive = sweetSpotBonusAwarded,
                        modifier = Modifier.weight(1f)
                    )

                    // Right side: Queue and Team Management
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Queue card showing teams in queue
                        FireballQueueCard(
                            participants = remainingParticipants,
                            modifier = Modifier.weight(1f)
                        )

                        // Team Management section
                        FireballTeamManagementCard(
                            onClearTeams = { showClearParticipantsDialog = true },
                            onImport = { filePicker.launch() },
                            onAddTeam = { showAddTeamDialog = true },
                            onExport = {
                                scope.launch {
                                    val templateBytes = assetLoader.load("templates/UDC Fireball Data Entry L1 Div Sort.xlsx")
                                    if (templateBytes == null) {
                                        println("Template missing: UDC Fireball Data Entry L1 Div Sort.xlsx")
                                        return@launch
                                    }

                                    val participants = completedParticipants
                                    if (participants.isEmpty()) {
                                        println("Export: no completed participants yet (press Next to commit a round)")
                                        return@launch
                                    }

                                    val exported = generateFireballXlsx(participants, templateBytes)
                                    if (exported.isEmpty()) {
                                        println("Export failed")
                                        return@launch
                                    }

                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    val stamp = fireballTimestamp(now)

                                    fileExporter.save("Fireball_Scores_$stamp.xlsx", exported)
                                    println("Exported Fireball_Scores_$stamp.xlsx")
                                }
                            },
                            onResetRound = { showResetRoundDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bottom row - Navigation buttons aligned below the grid
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
                                Text("⇄ FLIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Button(
                            onClick = { /* TODO: Add previous participant function */ },
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

        // Add Team Dialog
        if (showAddTeamDialog) {
            FireballAddParticipantDialog(
                onDismiss = { showAddTeamDialog = false },
                onAdd = { handler, dog, utn ->
                    screenModel.addParticipant(FireballParticipant(handler, dog, utn))
                    showAddTeamDialog = false
                }
            )
        }

        // Clear Teams Dialog
        if (showClearParticipantsDialog) {
            AlertDialog(
                onDismissRequest = { showClearParticipantsDialog = false },
                title = { Text("Clear All Participants?") },
                text = { Text("This will remove every participant from the queue. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.clearParticipantsQueue()
                        showClearParticipantsDialog = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearParticipantsDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Reset Round Dialog
        if (showResetRoundDialog) {
            AlertDialog(
                onDismissRequest = { showResetRoundDialog = false },
                title = { Text("Reset Round?") },
                text = { Text("Are you sure you want to reset the round? This will clear the current score and progress.") },
                confirmButton = {
                    Button(
                        onClick = {
                            screenModel.resetGame()
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
private fun FireballHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    activeParticipant: FireballParticipant?,
    totalScore: Int,
    boardScore: Int,
    onAllRollers: () -> Unit,
    allRollersActive: Boolean,
    onClearBoard: () -> Unit,
    onToggleFireball: () -> Unit,
    isFireballActive: Boolean,
    onToggleSweetSpot: () -> Unit,
    sweetSpotActive: Boolean,
    regularPoints: Int,
    fireballPoints: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Column 1: Home button, game title, active team, and Clear Board button
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
                            text = "Fire Ball",
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
                    onClick = onClearBoard, // Will be changed to onUndo
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF9100),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Column 2: Points display and Fireball/Sweet Spot buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Points display
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Regular:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                        Text(
                            text = regularPoints.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2979FF)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Fireball:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                        Text(
                            text = fireballPoints.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9100)
                        )
                    }
                }

                // Fireball button (tall, centered)
                Button(
                    onClick = onToggleFireball,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isFireballActive) Color(0xFFFF9100) else Color(0xFF757575),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(0.85f).height(72.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔥 FIREBALL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Column 3: Total Score, All Rollers, and Sweet Spot buttons
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
                        Text("Total: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = totalScore.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // All Rollers button
                    Button(
                        onClick = onAllRollers,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (allRollersActive) Color(0xFF00C853) else Color(0xFF757575),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Sweet Spot button below All Rollers
                    Button(
                        onClick = onToggleSweetSpot,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (sweetSpotActive) Color(0xFF00C853) else Color(0xFF757575),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SWEET SPOT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FireballTimerCard(
    timerRunning: Boolean,
    secondsRemaining: Int,
    audioFileName: String,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: TIMER and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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

            // Audio filename display
            Text(
                text = audioFileName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Timer display
            Box(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${secondsRemaining}s",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timerRunning) Color(0xFFFF9100) else Color.Black
                )
            }
        }
    }
}

@Composable
private fun FireballQueueCard(
    participants: List<FireballParticipant>,
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
private fun FireballTeamManagementCard(
    onClearTeams: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onExport: () -> Unit,
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
private fun FireballScoringGrid(
    screenModel: FireballScreenModel,
    isFieldFlipped: Boolean,
    isFireballActive: Boolean,
    sweetSpotActive: Boolean,
    modifier: Modifier = Modifier
) {
    val clickedZones by screenModel.clickedZones.collectAsState()
    val fireballZones by screenModel.fireballZones.collectAsState()

    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3x5 grid layout (matching ThrowNGo structure)
            // Fireball's 4x3 grid will be in center 3 columns, with empty cells on left and right

            val fireballRows = FireballScreenModel.zoneValueGrid.indices.toList() // 0,1,2,3
            val fireballCols = FireballScreenModel.zoneValueGrid.first().indices.toList() // 0,1,2

            val rowOrder = if (isFieldFlipped) fireballRows.reversed() else fireballRows
            val colOrder = if (isFieldFlipped) fireballCols.reversed() else fireballCols

            // Row weights: rows 0,2,3 are regular height, row 1 (middle) is taller
            val rowHeights = listOf(1f, 2f, 1f, 1f)

            rowOrder.forEach { fireballRow ->
                val rowWeight = rowHeights[fireballRow]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(rowWeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 5 columns: empty, fireball, fireball, fireball, empty
                    for (displayCol in 0..4) {
                        when (displayCol) {
                            0, 4 -> {
                                // Empty cell on left and right edges
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE0E0E0)) // Gray empty cell
                                )
                            }
                            1, 2, 3 -> {
                                // Fireball buttons in center 3 columns
                                val fireballCol = displayCol - 1 // Map to 0,1,2
                                val actualCol = if (isFieldFlipped) {
                                    // When flipped, reverse the center columns
                                    2 - fireballCol
                                } else {
                                    fireballCol
                                }

                                val zoneValue = FireballScreenModel.zoneValue(fireballRow, actualCol)
                                val point = FireballGridPoint(fireballRow, actualCol)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (zoneValue != null) {
                                        ZoneButton(
                                            value = zoneValue,
                                            clicked = clickedZones.contains(point),
                                            fireball = fireballZones.contains(point),
                                            onClick = { screenModel.handleZoneClick(fireballRow, actualCol) }
                                        )
                                    }
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
private fun ZoneButton(value: Int, clicked: Boolean, fireball: Boolean, onClick: () -> Unit) {
    val colors = when {
        fireball -> ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFFFF9100), // Orange for fireball
            contentColor = Color.White
        )
        clicked -> ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFF00C853), // Green for clicked
            contentColor = Color.White
        )
        else -> ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFFF500A1), // Pink default
            contentColor = Color.White
        )
    }
    Button(
        onClick = onClick,
        enabled = true,
        colors = colors,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FireballAddParticipantDialog(
    onDismiss: () -> Unit,
    onAdd: (handler: String, dog: String, utn: String) -> Unit
) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material.OutlinedTextField(
                    value = handler,
                    onValueChange = { handler = it },
                    label = { Text("Handler") },
                    singleLine = true
                )
                androidx.compose.material.OutlinedTextField(
                    value = dog,
                    onValueChange = { dog = it },
                    label = { Text("Dog") },
                    singleLine = true
                )
                androidx.compose.material.OutlinedTextField(
                    value = utn,
                    onValueChange = { utn = it },
                    label = { Text("UTN") },
                    singleLine = true
                )
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

private fun fireballTimestamp(now: kotlinx.datetime.LocalDateTime): String {
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
