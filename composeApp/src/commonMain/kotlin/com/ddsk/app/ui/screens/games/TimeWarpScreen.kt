package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.TextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
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
import com.ddsk.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

object TimeWarpScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { TimeWarpScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        // State access
        val score by screenModel.score.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()
        val canUndo by screenModel.canUndo.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpotClicked by screenModel.sweetSpotClicked.collectAsState()
        val allRollersClicked by screenModel.allRollersClicked.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val timeRemaining by screenModel.timeRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val isAudioTimerPlaying by screenModel.isAudioTimerPlaying.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantQueue by screenModel.participantQueue.collectAsState()
        val completedParticipants by screenModel.completedParticipants.collectAsState()

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()

        var showAddParticipant by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }
        var showTeams by remember { mutableStateOf(false) }
        // TODO: Add back for future iteration
        // var showHelp by remember { mutableStateOf(false) }
        var showTimeInput by remember { mutableStateOf(false) }

        // Import / Export
        val assetLoader = rememberAssetLoader()
        val exporter = rememberFileExporter()
        val scope = rememberCoroutineScope()
        var showImportChoice by remember { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<ImportResult?>(null) }

        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImport = result
                    showImportChoice = true
                }
                else -> Unit
            }
        }

        // When model emits a pending JSON export, prompt user to save it.
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        // Audio timer setup
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Time Warp") })
        val currentAudioTime by audioPlayer.currentTime.collectAsState()
        val audioRemainingSeconds = remember(audioPlayer.duration, currentAudioTime) {
            ((audioPlayer.duration - currentAudioTime) / 1000).coerceAtLeast(0)
        }

        LaunchedEffect(isAudioTimerPlaying) {
            if (isAudioTimerPlaying) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        // Add team dialog state
        var handler by remember { mutableStateOf("") }
        var dog by remember { mutableStateOf("") }
        var utn by remember { mutableStateOf("") }

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
                    TimeWarpHeaderCard(
                        navigator = navigator,
                        score = score,
                        misses = misses,
                        ob = ob,
                        activeParticipant = activeParticipant,
                        canUndo = canUndo,
                        onUndo = { screenModel.undoLastAction() },
                        onMiss = { screenModel.incrementMisses() },
                        onOb = { screenModel.incrementOb() },
                        onShowTeams = { showTeams = true },
                        allRollersClicked = allRollersClicked,
                        onAllRollersClick = { screenModel.toggleAllRollers() },
                        timeRemaining = timeRemaining,
                        isTimerRunning = isTimerRunning,
                        onStartStopCountdown = {
                            if (isTimerRunning) screenModel.stopCountdownAndAddScore() else screenModel.startCountdown()
                        },
                        onLongPressStart = { showTimeInput = true },
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    )

                    // Right: Timer card
                    TimeWarpTimerCard(
                        isAudioTimerPlaying = isAudioTimerPlaying,
                        timeLeft = audioRemainingSeconds,
                        onTimerAudioToggle = screenModel::toggleAudioTimer,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Middle row: Main game grid and Team Management
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main grid (center)
                    FieldGrid(
                        clickedZones = clickedZones,
                        fieldFlipped = fieldFlipped,
                        onZoneClick = { screenModel.handleZoneClick(it) },
                        sweetSpotClicked = sweetSpotClicked,
                        onSweetSpotClick = { screenModel.handleSweetSpotClick() },
                        allRollersClicked = allRollersClicked,
                        onAllRollersClick = { screenModel.toggleAllRollers() },
                        modifier = Modifier.weight(1f)
                    )

                    // Right side: Queue and Team Management
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Queue card showing teams in queue
                        TimeWarpQueueCard(
                            active = activeParticipant,
                            queue = participantQueue,
                            modifier = Modifier.weight(1f)
                        )

                        // Team Management section
                        TimeWarpTeamManagementCard(
                            onClearTeams = { showClearTeamsDialog = true },
                            onImport = { filePicker.launch() },
                            onAddTeam = { showAddParticipant = true },
                            onExport = {
                                val template = assetLoader.load("templates/UDC TimeWarp Data Entry L1 or L2 Div Sort.xlsx")
                                if (template != null) {
                                    val all = buildList {
                                        activeParticipant?.let { add(it) }
                                        addAll(participantQueue)
                                        addAll(completedParticipants)
                                    }
                                    val bytes = generateTimeWarpXlsx(all, template)
                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    val stamp = timeWarpTimestamp(now)
                                    exporter.save("TimeWarp_Scores_$stamp.xlsx", bytes)
                                }
                            },
                            onLog = { /* LOG */ },
                            // TODO: Add back for future iteration
                            // onHelp = { showHelp = true },
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
                            onClick = { screenModel.flipField() },
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                            onClick = { /* Previous */ },
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                            onClick = { screenModel.nextParticipant() },
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                            onClick = { screenModel.skipParticipant() },
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(
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

        if (showTeams) {
            TeamsDialog(
                active = activeParticipant,
                queue = participantQueue,
                completed = completedParticipants,
                onDismiss = { showTeams = false }
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
                                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(importRes.contents, TimeWarpScreenModel.ImportMode.Add)
                                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(importRes.bytes, TimeWarpScreenModel.ImportMode.Add)
                                    else -> Unit
                                }
                            }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val importRes = pendingImport
                                showImportChoice = false
                                pendingImport = null
                                if (importRes != null) {
                                    when (importRes) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(importRes.contents, TimeWarpScreenModel.ImportMode.ReplaceAll)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(importRes.bytes, TimeWarpScreenModel.ImportMode.ReplaceAll)
                                        else -> Unit
                                    }
                                }
                            }
                        ) { Text("Replace") }
                        TextButton(onClick = { showImportChoice = false; pendingImport = null }) { Text("Cancel") }
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
                            // Call clear teams function when implemented
                            // screenModel.clearParticipants()
                            showClearTeamsDialog = false
                        },
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                text = { Text("Are you sure you want to reset the current round? All scores will be lost. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            screenModel.reset()
                            showResetRoundDialog = false
                        },
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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

        if (showAddParticipant) {
            AlertDialog(
                onDismissRequest = { showAddParticipant = false },
                title = { Text("Add Team") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") })
                        TextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") })
                        TextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") })
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            screenModel.importParticipantsFromCsv(
                                "handler,dog,utn\n${handler.trim()},${dog.trim()},${utn.trim()}",
                                TimeWarpScreenModel.ImportMode.Add
                            )
                            handler = ""; dog = ""; utn = ""
                            showAddParticipant = false
                        }
                    ) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showAddParticipant = false }) { Text("Cancel") } }
            )
        }

        if (showTimeInput) {
            var timeText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showTimeInput = false },
                title = { Text("Set Time Remaining") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Enter the time remaining in seconds (e.g., 11.50).\nThis value will be added to your score.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        TextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("Seconds") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("e.g., 11.50") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        // manual entry adds rounded value to score
                        screenModel.applyManualTimeAndAddScore(timeText)
                        showTimeInput = false
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { showTimeInput = false }) { Text("Cancel") } }
            )
        }

        // TODO: Add back help dialog in future iteration
        // if (showHelp) {
        //     AlertDialog(
        //         onDismissRequest = { showHelp = false },
        //         confirmButton = { Button(onClick = { showHelp = false }) { Text("Close") } },
        //         title = { Text("TimeWarp — Help") },
        //         text = { Text("- Timer: plays the 60s timer audio.\n- Export: exports participants.\n- Add Team: opens add participant modal.\n- Reset Score: resets scoring and stops timers.\n- Flip Field: toggles field orientation.\n- Zone buttons: mark zones as clicked and update score.\n- Sweet Spot: toggles sweet spot bonus.") }
        //     )
        // }
    }
}

@Composable
private fun TimeWarpHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    score: Int,
    misses: Int,
    ob: Int,
    activeParticipant: TimeWarpParticipant?,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    onShowTeams: () -> Unit,
    allRollersClicked: Boolean,
    onAllRollersClick: () -> Unit,
    timeRemaining: Float,
    isTimerRunning: Boolean,
    onStartStopCountdown: () -> Unit,
    onLongPressStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Column 1: Title, UNDO button, and TIME WARP COMPLETED button
            Column(
                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameHomeButton(navigator = navigator)
                        Text(
                            text = "Time Warp",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = activeParticipant?.displayName ?: "No active team",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.clickable { onShowTeams() }
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { /* Time Warp Completed */ },
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF6750A4),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.width(140.dp).height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("TIME WARP\nCOMPLETED", fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                    }

                    Button(
                        onClick = onUndo,
                        enabled = canUndo,
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.width(140.dp).height(38.dp)
                    ) {
                        Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Column 2: MISS and OB buttons at bottom
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                // MISS and OB buttons at bottom in same row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Miss button (red background)
                    Button(
                        onClick = onMiss,
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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

            // Column 3: Score display, START button, Time display, and All Rollers button
            Column(
                modifier = Modifier.weight(0.8f).fillMaxHeight(),
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
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Start/Stop button
                    Card(
                        backgroundColor = if (isTimerRunning) Color(0xFFD50000) else Color(0xFF6750A4),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onStartStopCountdown() },
                                    onLongPress = { onLongPressStart() }
                                )
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                if (isTimerRunning) "STOP" else "START",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Time Remaining display
                    Text(
                        "Time: ${formatHundredths(timeRemaining)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    // All Rollers button
                    Button(
                        onClick = onAllRollersClick,
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
                            backgroundColor = if (allRollersClicked) Color(0xFF00C853) else Color(0xFF9E9E9E),
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
private fun TimeWarpTimerCard(
    isAudioTimerPlaying: Boolean,
    timeLeft: Int,
    onTimerAudioToggle: () -> Unit,
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
                    onClick = { if (!isAudioTimerPlaying) onTimerAudioToggle() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                    onClick = { if (isAudioTimerPlaying) onTimerAudioToggle() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                    text = if (isAudioTimerPlaying) "${timeLeft}s" else "Ready",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAudioTimerPlaying) Color(0xFF00BCD4) else Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                if (isAudioTimerPlaying) {
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
private fun TimeWarpQueueCard(
    active: TimeWarpParticipant?,
    queue: List<TimeWarpParticipant>,
    modifier: Modifier = Modifier
) {
    // Show all teams that do NOT have scoring data attached.
    val pendingTeams = buildList {
        if (active?.result == null) {
            active?.let { add(it) }
        }
        addAll(queue.filter { it.result == null })
    }

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
                if (pendingTeams.isEmpty()) {
                    item {
                        Text(
                            "No teams in queue",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(count = pendingTeams.size) { index ->
                        val participant = pendingTeams[index]
                        val isCurrentTeam = index == 0 && active == participant
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
                                    text = if (isCurrentTeam) "▶ ${participant.displayName}"
                                           else "${participant.displayName}",
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
private fun TimeWarpTeamManagementCard(
    onClearTeams: () -> Unit,
    onImport: () -> Unit,
    onAddTeam: () -> Unit,
    onExport: () -> Unit,
    onLog: () -> Unit,
    // TODO: Add back for future iteration
    // onHelp: () -> Unit,
    onResetRound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Scrollable content
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClearTeams,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
                        colors = androidx.compose.material.ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF7B1FA2),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("LOG", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // TODO: Add HELP button in future iteration
                // Button(
                //     onClick = onHelp,
                //     modifier = Modifier.fillMaxWidth().height(42.dp),
                //     colors = androidx.compose.material.ButtonDefaults.buttonColors(
                //         backgroundColor = Color(0xFF7B1FA2),
                //         contentColor = Color.White
                //     ),
                //     shape = RoundedCornerShape(8.dp)
                // ) {
                //     Text("HELP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                // }
            }

            // Fixed RESET ROUND button at bottom
            Button(
                onClick = onResetRound,
                modifier = Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                colors = androidx.compose.material.ButtonDefaults.buttonColors(
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
private fun ScoreHeader(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    score: Int,
    misses: Int,
    ob: Int,
    timer: Float,
    isTimerRunning: Boolean,
    isAudioTimerPlaying: Boolean,
    onTimerAudioToggle: () -> Unit,
    onStartStopCountdown: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    activeParticipant: TimeWarpParticipant?,
    onShowTeams: () -> Unit,
    onLongPressStart: () -> Unit
) {
    Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GameHomeButton(navigator = navigator)

                // Remove Modifier.weight usage
                Column(modifier = Modifier.width(320.dp).clickable { onShowTeams() }) {
                    Text("Current Team", color = Palette.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = activeParticipant?.displayName ?: "No Team Loaded",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Separate audio vs countdown controls
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onTimerAudioToggle,
                            colors = androidx.compose.material.ButtonDefaults.buttonColors(
                                backgroundColor = if (isAudioTimerPlaying) Palette.warning else Palette.info,
                                contentColor = if (isAudioTimerPlaying) Palette.onWarning else Palette.onInfo
                            )
                        ) {
                            Text(if (isAudioTimerPlaying) "Timer (On)" else "Timer")
                        }

                        StartStopButton(
                            time = timer,
                            isRunning = isTimerRunning,
                            onClick = onStartStopCountdown,
                            onLongPress = onLongPressStart
                        )
                    }
                    Text(
                        text = formatHundredths(timer),
                        color = Palette.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScoreBox(label = "Score", value = score.toString(), modifier = Modifier.weight(1f))

                    Button(onClick = onMiss, modifier = Modifier.width(120.dp)) {
                        Text("Miss: $misses")
                    }

                    Button(onClick = onOb, modifier = Modifier.width(120.dp)) {
                        Text("OB: $ob")
                    }

                    Button(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.width(92.dp)
                    ) {
                        Text("Undo")
                    }
                }
            }
        }
    }
}

@Composable
private fun StartStopButton(
    time: Float,
    isRunning: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val color = if (isRunning) Palette.error else Palette.primary
    val label = if (isRunning) "Stop" else "Start"

    Card(
        backgroundColor = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(120.dp)
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(formatHundredths(time), color = Color.White)
        }
    }
}

@Composable
private fun ActionButton2Col(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        // Use fillMaxWidth in a Row with Modifier.weight supplied by RowScope via extension receiver.
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatHundredths(seconds: Float): String {
    val clamped = if (seconds < 0f) 0f else seconds
    val totalHundredths = (clamped * 100f).roundToInt()
    val mins = totalHundredths / 6000
    val secs = (totalHundredths % 6000) / 100
    val hund = totalHundredths % 100

    fun pad2(n: Int) = n.toString().padStart(2, '0')
    return "${pad2(mins)}:${pad2(secs)}.${pad2(hund)}"
}

@Composable
private fun ScoreBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = Palette.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
    }
}

@Composable
private fun FieldGrid(
    clickedZones: Set<Int>,
    fieldFlipped: Boolean,
    onZoneClick: (Int) -> Unit,
    sweetSpotClicked: Boolean,
    onSweetSpotClick: () -> Unit,
    allRollersClicked: Boolean,
    onAllRollersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3 rows x 5 cols; middle row taller. Zones: 1 (col0 all rows), 2 (col1 all rows), 3 (col2 middle row)
    val rows = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
    val cols = if (fieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

    Card(
        elevation = 8.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (r == 1) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    cols.forEach { c ->
                        val zoneNumber = when {
                            c == 0 -> 1
                            c == 1 -> 2
                            c == 2 && r == 1 -> 3
                            else -> null
                        }

                        // Special buttons in columns 3 and 4
                        when {
                            c == 3 && r == 1 -> {
                                // Sweet Spot button
                                Button(
                                    onClick = onSweetSpotClick,
                                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                                        backgroundColor = if (sweetSpotClicked) Color(0xFF00C853) else Color(0xFFF500A1),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Sweet\nSpot", fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                            }
                            c == 4 && r == 1 -> {
                                // Empty space (light gray background)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                )
                            }
                            zoneNumber != null -> {
                                // Zone button
                                val clicked = clickedZones.contains(zoneNumber)
                                val bg = if (clicked) Color(0xFF00C853) else Color(0xFFF500A1)

                                Button(
                                    onClick = { onZoneClick(zoneNumber) },
                                    enabled = !clicked,
                                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                                        backgroundColor = bg,
                                        contentColor = Color.White,
                                        disabledBackgroundColor = bg,
                                        disabledContentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$zoneNumber",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            else -> {
                                // Empty space (light gray background)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
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
private fun BottomControls(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onShowTeams: () -> Unit,
    onAllRollers: () -> Unit,
    allRollersActive: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onSkip, modifier = Modifier.width(110.dp)) { Text("Skip") }
        Button(onClick = onAllRollers, modifier = Modifier.width(150.dp)) { Text(if (allRollersActive) "All Rollers (On)" else "All Rollers") }
        Button(onClick = onNext, modifier = Modifier.width(110.dp)) { Text("Next") }
    }
}

@Composable
private fun ActionButtons(
    sweetSpotActive: Boolean,
    onSweetSpot: () -> Unit,
    onFlipField: () -> Unit,
    onReset: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit
) {
    Column {
        Button(onClick = onSweetSpot) { Text(if (sweetSpotActive) "Sweet Spot (On)" else "Sweet Spot") }
        Button(onClick = onFlipField) { Text("Flip Field") }
        Button(onClick = onReset) { Text("Reset") }
        Button(onClick = onAddTeam) { Text("Add Team") }
        Button(onClick = onHelp) { Text("Help") }
    }
}

@Composable
private fun TeamsDialog(
    active: TimeWarpParticipant?,
    queue: List<TimeWarpParticipant>,
    completed: List<TimeWarpParticipant>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text("Teams") },
        text = {
            LazyColumn {
                item { Text("Active: ${active?.displayName ?: "None"}") }
                item { Text("Queue: ${queue.size}") }
                itemsIndexed(queue) { idx, item ->
                    Text("${idx + 1}. ${item.displayName}")
                }
                item { Text("Completed: ${completed.size}") }
                itemsIndexed(completed) { _, item ->
                    Text("${item.displayName} - ${item.result?.score}")
                }
            }
        }
    )
}

private fun Float.formatTime(): String {
    val totalSeconds = this.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun QueueCard(
    active: TimeWarpParticipant?,
    queue: List<TimeWarpParticipant>
) {
    // Show all teams that do NOT have scoring data attached.
    // Include the active team if it also has no result yet.
    val pendingTeams = buildList {
        if (active?.result == null) {
            active?.let { add(it) }
        }
        addAll(queue.filter { it.result == null })
    }

    Card(
        elevation = 6.dp,
        backgroundColor = Palette.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Queue", color = Palette.onSurfaceVariant, fontSize = 12.sp)

            if (pendingTeams.isEmpty()) {
                Text(
                    text = "No teams pending",
                    color = Palette.onSurface,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    itemsIndexed(pendingTeams) { idx, team ->
                        val prefix = if (idx == 0 && active == team) "Active: " else "${idx + 1}. "
                        Text(
                            text = prefix + (team.displayName),
                            color = Palette.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private fun timeWarpTimestamp(now: kotlinx.datetime.LocalDateTime): String {
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
