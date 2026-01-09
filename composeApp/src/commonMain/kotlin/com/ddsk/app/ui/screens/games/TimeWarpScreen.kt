package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt

// Add import definitions
import androidx.compose.material.TextButton
import cafe.adriel.voyager.koin.getScreenModel

@OptIn(ExperimentalFoundationApi::class)
object TimeWarpScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        Surface(color = MD3Colors.background, modifier = Modifier.fillMaxSize()) {
            TimeWarpScreenContent(
                onNavigateHome = { navigator?.pop() }
            )
        }
    }
}

@Composable
private fun TimeWarpScreenContent(onNavigateHome: () -> Unit) {
    val scrollState = rememberScrollState()

    var score by rememberSaveable { mutableStateOf(0) }
    var timer by rememberSaveable { mutableStateOf(60.0f) }
    var isTimerRunning by rememberSaveable { mutableStateOf(false) }
    var misses by rememberSaveable { mutableStateOf(0) }
    var ob by rememberSaveable { mutableStateOf(0) }
    var clickedZones by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var sweetSpotActive by rememberSaveable { mutableStateOf(false) }
    var allRollersActive by rememberSaveable { mutableStateOf(false) }
    var pointsAwarded by rememberSaveable { mutableStateOf(false) }
    var fieldFlipped by rememberSaveable { mutableStateOf(false) }

    val participants = remember { mutableStateListOf<TimeWarpParticipant>() }
    val completedParticipants = remember { mutableStateListOf<TimeWarpParticipant>() }
    var currentParticipantIndex by rememberSaveable { mutableStateOf(0) }

    val logEntries = remember { mutableStateListOf<LogEntry>() }
    var logMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val jsonEncoder = remember {
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }

    var showHelp by remember { mutableStateOf(false) }
    var showAddTeam by remember { mutableStateOf(false) }
    var showTimePrompt by remember { mutableStateOf(false) }
    var timePromptValue by remember { mutableStateOf("60.00") }
    var showLogResetPrompt by remember { mutableStateOf(false) }
    var showClearParticipantsPrompt by remember { mutableStateOf(false) }
    var showTieWarning by remember { mutableStateOf(false) }
    var showTeamModal by remember { mutableStateOf(false) }
    var showEndOfList by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<TimeWarpSnapshot>() }

    val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Time Warp") })
    var audioPlaying by remember { mutableStateOf(false) }
    val audioCurrentMillis by audioPlayer.currentTime.collectAsState()
    val audioDurationMillis = audioPlayer.duration

    fun logButton(label: String, extra: String? = null) {
        val timestamp = Clock.System.now().toString()
        logEntries.add(LogEntry(timestamp, label, extra))
    }

    fun resetRoundState() {
        score = 0
        timer = 60.0f
        isTimerRunning = false
        misses = 0
        ob = 0
        clickedZones = emptySet()
        sweetSpotActive = false
        allRollersActive = false
        pointsAwarded = false
    }

    suspend fun exportLog() {
        if (logEntries.isEmpty()) return
        val payload = LogExportPayload(
            createdAt = Clock.System.now().toString(),
            entries = logEntries.toList()
        )
        val filename = "timewarp_log_${Clock.System.now().toEpochMilliseconds()}.json"
        shareJsonFile(filename, jsonEncoder.encodeToString(payload))
    }

    fun snapshot(action: String) {
        undoStack.add(0, TimeWarpSnapshot(score, timer, isTimerRunning, misses, ob, clickedZones, sweetSpotActive, allRollersActive, action))
        if (undoStack.size > 50) undoStack.removeLast()
    }

    fun completeActiveParticipant() {
        val participant = participants.getOrNull(currentParticipantIndex) ?: return
        val result = TimeWarpRoundResult(
            score = score,
            timeRemaining = timer,
            misses = misses,
            zonesCaught = clickedZones.size,
            sweetSpot = sweetSpotActive,
            allRollers = allRollersActive
        )
        participants.removeAt(currentParticipantIndex)
        completedParticipants.add(participant.copy(result = result))

        if (participants.isEmpty()) {
            currentParticipantIndex = 0
            showEndOfList = true
        } else {
            currentParticipantIndex = currentParticipantIndex.coerceAtMost(participants.lastIndex)
        }
        resetRoundState()
    }

    fun detectTie(): Boolean {
        val sorted = completedParticipants.sortedWith(compareByDescending<TimeWarpParticipant> { it.result?.score ?: 0 }
            .thenByDescending { it.result?.timeRemaining ?: 0f })
        val window = sorted.take(5)
        return window.zipWithNext().any { (a, b) ->
            val scoreA = a.result?.score ?: -1
            val scoreB = b.result?.score ?: -2
            val timeA = a.result?.timeRemaining?.roundToInt() ?: -1
            val timeB = b.result?.timeRemaining?.roundToInt() ?: -2
            scoreA == scoreB && timeA == timeB
        }
    }

    LaunchedEffect(isTimerRunning) {
        if (!isTimerRunning) return@LaunchedEffect
        val startTime = System.currentTimeMillis()
        val initial = timer
        while (isActive && isTimerRunning) {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
            val remaining = max(0f, initial - elapsed)
            timer = (remaining * 100).roundToInt() / 100f
            if (remaining <= 0f) {
                isTimerRunning = false
                if (!pointsAwarded) {
                    score += timer.roundToInt()
                    pointsAwarded = true
                }
            }
            delay(16L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TimeWarpTopBar(
            score = score,
            timer = timer,
            isTimerRunning = isTimerRunning,
            misses = misses,
            ob = ob,
            sweetSpotActive = sweetSpotActive,
            sweetSpotEnabled = clickedZones.size == 3,
            undoCount = undoStack.size,
            onTimerStart = {
                snapshot("Start Timer")
                logButton("Start Timer")
                isTimerRunning = true
            },
            onTimerStop = {
                snapshot("Stop Timer")
                logButton("Stop Timer", "${timer.formatTime()} remaining")
                isTimerRunning = false
                if (!pointsAwarded) {
                    score += timer.roundToInt()
                    pointsAwarded = true
                }
            },
            onTimerLongPress = {
                timePromptValue = timer.formatTime()
                showTimePrompt = true
            },
            onSweetSpotClick = {
                if (clickedZones.size == 3) {
                    snapshot("Sweet Spot")
                    sweetSpotActive = !sweetSpotActive
                    score += if (sweetSpotActive) 25 else -25
                    logButton("Sweet Spot")
                }
            },
            onMissClick = {
                snapshot("Miss")
                misses++
                logButton("Miss")
            },
            onObClick = {
                snapshot("OB")
                ob++
                logButton("OB")
            },
            onUndo = {
                val restore = undoStack.removeFirstOrNull() ?: return@TimeWarpTopBar
                score = restore.score
                timer = restore.timer
                isTimerRunning = restore.timerRunning
                misses = restore.misses
                ob = restore.ob
                clickedZones = restore.clickedZones
                sweetSpotActive = restore.sweetSpot
                allRollersActive = restore.allRollers
                logButton("Undo", restore.action)
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Sidebar(
                collapsedState = remember { mutableStateOf(false) },
                importing = false,
                exporting = false,
                onImport = {
                    snapshot("Import")
                    logButton("Import Participants")
                },
                onExport = {
                    if (detectTie()) {
                        showTieWarning = true
                    } else {
                        snapshot("Export")
                        logButton("Export Participants")
                    }
                },
                onLog = {
                    logMenuExpanded = true
                },
                onAddTeam = { showAddTeam = true },
                onHelp = { showHelp = true },
                onReset = {
                    snapshot("Reset Round")
                    logButton("Reset Round")
                    resetRoundState()
                },
                onFlipField = {
                    fieldFlipped = !fieldFlipped
                    logButton("Flip Field", if (fieldFlipped) "Flipped" else "Default")
                },
                onClearParticipants = { showClearParticipantsPrompt = true },
                onClearLog = { showLogResetPrompt = true }
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ParticipantHeader(
                    activeParticipant = participants.getOrNull(currentParticipantIndex),
                    audioPlaying = audioPlaying,
                    audioRemaining = ((audioDurationMillis - audioCurrentMillis) / 1000f).coerceAtLeast(0f),
                    onPlayAudio = {
                        if (audioPlaying) {
                            audioPlayer.stop()
                            audioPlaying = false
                        } else {
                            audioPlayer.stop()
                            audioPlayer.play()
                            audioPlaying = true
                        }
                        logButton("Audio Timer", if (audioPlaying) "Playing" else "Stopped")
                    }
                )

                RemainingTeamsCard(participants = participants, currentIndex = currentParticipantIndex)

                TimeWarpField(
                    flipped = fieldFlipped,
                    clickedZones = clickedZones,
                    onZoneClick = { zone ->
                        if (zone !in clickedZones) {
                            snapshot("Zone $zone")
                            logButton("Zone $zone")
                            clickedZones = clickedZones + zone
                            score += 5
                        }
                    },
                    zoneEnabled = { zone -> zone !in clickedZones }
                )

                ControlRow(
                    onAllRollers = {
                        snapshot("All Rollers")
                        allRollersActive = !allRollersActive
                        logButton("All Rollers", if (allRollersActive) "Active" else "Inactive")
                    },
                    allRollersActive = allRollersActive,
                    onPrevious = {
                        if (completedParticipants.isNotEmpty()) {
                            val last = completedParticipants.removeLast()
                            participants.add(0, last.copy(result = null))
                            currentParticipantIndex = 0
                            logButton("Previous Participant")
                        }
                    },
                    onNext = {
                        logButton("Next Participant")
                        completeActiveParticipant()
                    },
                    onSkip = {
                        if (participants.size > 1) {
                            val current = participants.removeAt(currentParticipantIndex)
                            participants.add(current)
                            currentParticipantIndex = currentParticipantIndex.coerceAtMost(participants.lastIndex)
                            logButton("Skip Participant")
                        }
                    },
                    onShowTeams = { showTeamModal = true }
                )

                Box {
                    DropdownMenu(expanded = logMenuExpanded, onDismissRequest = { logMenuExpanded = false }) {
                        DropdownMenuItem(onClick = {
                            snapshot("Export Log")
                            logButton("Export Log")
                            scope.launch {
                                exportLog()
                                logMenuExpanded = false
                            }
                        }) { Text("Export JSON") }
                        DropdownMenuItem(onClick = {
                            showLogResetPrompt = true
                            logMenuExpanded = false
                        }) { Text("Reset Log") }
                    }
                }
            }
        }

        if (showHelp) {
            InfoDialog(
                title = "Help — Button Functions",
                text = TIMEWARP_HELP_TEXT,
                onDismiss = { showHelp = false }
            )
        }

        if (showAddTeam) {
            AddTeamDialog(
                onDismiss = { showAddTeam = false },
                onAdd = { handler, dog, utn ->
                    participants.add(TimeWarpParticipant(handler, dog, utn))
                    if (participants.size == 1) currentParticipantIndex = 0
                    logButton("Add Team", "$handler & $dog")
                }
            )
        }

        if (showTimePrompt) {
            TimePromptDialog(
                value = timePromptValue,
                onValueChange = { timePromptValue = it },
                onDismiss = { showTimePrompt = false },
                onConfirm = {
                    val parsed = it.toFloatOrNull()?.coerceAtLeast(0f) ?: return@TimePromptDialog
                    snapshot("Manual Time")
                    timer = parsed
                    if (!pointsAwarded) {
                        score += parsed.roundToInt()
                        pointsAwarded = true
                    }
                    showTimePrompt = false
                    logButton("Manual Time", parsed.formatTime())
                }
            )
        }

        if (showLogResetPrompt) {
            ConfirmDialog(
                title = "Reset Log?",
                body = "This clears the entire log history.",
                confirmText = "Reset",
                onConfirm = {
                    logEntries.clear()
                },
                onDismiss = { showLogResetPrompt = false }
            )
        }

        if (showClearParticipantsPrompt) {
            ConfirmDialog(
                title = "Clear Participants?",
                body = "Remove all participants and results.",
                confirmText = "Clear",
                onConfirm = {
                    participants.clear()
                    completedParticipants.clear()
                    currentParticipantIndex = 0
                    resetRoundState()
                    showClearParticipantsPrompt = false
                },
                onDismiss = { showClearParticipantsPrompt = false }
            )
        }

        if (showTieWarning) {
            InfoDialog(
                title = "Unresolved Tie",
                text = "Top 5 contains a tie. Please resolve before exporting.",
                onDismiss = { showTieWarning = false }
            )
        }

        if (showEndOfList) {
            ConfirmDialog(
                title = "End of Team List",
                body = "All teams completed. Restart?",
                confirmText = "Restart",
                onConfirm = {
                    participants.addAll(completedParticipants.map { it.copy(result = null) })
                    completedParticipants.clear()
                    currentParticipantIndex = 0
                    showEndOfList = false
                },
                onDismiss = { showEndOfList = false }
            )
        }

        if (showTeamModal) {
            TeamModal(
                participants = participants,
                completed = completedParticipants,
                onDismiss = { showTeamModal = false }
            )
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            FloatingHomeButton(onClick = onNavigateHome)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun TimeWarpTopBar(
    score: Int,
    timer: Float,
    isTimerRunning: Boolean,
    misses: Int,
    ob: Int,
    sweetSpotActive: Boolean,
    sweetSpotEnabled: Boolean,
    undoCount: Int,
    onTimerStart: () -> Unit,
    onTimerStop: () -> Unit,
    onTimerLongPress: () -> Unit,
    onSweetSpotClick: () -> Unit,
    onMissClick: () -> Unit,
    onObClick: () -> Unit,
    onUndo: () -> Unit
) {
    Card(elevation = 6.dp, backgroundColor = MD3Colors.surface, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TimerButton(
                    isTimerRunning = isTimerRunning,
                    timer = timer,
                    onStart = onTimerStart,
                    onStop = onTimerStop,
                    onLongPress = onTimerLongPress,
                    modifier = Modifier.weight(1f)
                )
                ScoreBadge(score = score)
                StatChip(
                    label = "Sweet Spot",
                    value = if (sweetSpotActive) "ON" else "OFF",
                    color = if (sweetSpotActive) MD3Colors.success else MD3Colors.info,
                    enabled = sweetSpotEnabled,
                    onClick = onSweetSpotClick
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatChip(label = "Misses", value = "$misses", color = MD3Colors.error, onClick = onMissClick)
                StatChip(label = "OB", value = "$ob", color = MD3Colors.warning, onClick = onObClick)
                Button(onClick = onUndo, enabled = undoCount > 0) { Text("Undo (${undoCount})", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TimerButton(
    isTimerRunning: Boolean,
    timer: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (isTimerRunning) "Stop" else "Start"
    Button(
        onClick = {
            if (isTimerRunning) onStop() else onStart()
        },
        modifier = modifier.pointerInput(isTimerRunning) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isTimerRunning) VIBRANTColors.warning else VIBRANTColors.info
        ),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = timer.formatTime() + "s", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MD3Colors.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Score", color = MD3Colors.onSurfaceVariant, fontSize = 12.sp)
        Text("$score", color = MD3Colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val activeColor = if (enabled) color else color.copy(alpha = 0.6f)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = activeColor,
            contentColor = Color.White,
            disabledBackgroundColor = activeColor,
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Sidebar(
    collapsedState: MutableState<Boolean>,
    importing: Boolean,
    exporting: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLog: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
    onFlipField: () -> Unit,
    onClearParticipants: () -> Unit,
    onClearLog: () -> Unit
) {
    val collapsed by collapsedState
    Column(
        modifier = Modifier
            .width(if (collapsed) 72.dp else 180.dp)
            .background(MD3Colors.surfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, MD3Colors.outline, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { collapsedState.value = !collapsed }, modifier = Modifier.fillMaxWidth()) {
            Text(if (collapsed) "☰" else "Hide ✕")
        }
        if (!collapsed) {
            SidebarButton(text = if (importing) "Importing..." else "Import", color = MD3Colors.primary, onClick = onImport)
            SidebarButton(text = if (exporting) "Exporting..." else "Export", color = MD3Colors.primary, onClick = onExport)
            SidebarButton(text = "Log", color = MD3Colors.primary, onClick = onLog)
            SidebarButton(text = "Add Team", color = MD3Colors.info, onClick = onAddTeam)
            SidebarButton(text = "Help", color = MD3Colors.success, onClick = onHelp)
            SidebarButton(text = "Reset Score", color = MD3Colors.error, onClick = onReset)
            SidebarButton(text = "Flip Field", color = MD3Colors.secondary, onClick = onFlipField)
            SidebarButton(text = "Clear Teams", color = MD3Colors.warning, onClick = onClearParticipants)
            SidebarButton(text = "Clear Log", color = MD3Colors.warning, onClick = onClearLog)
        }
    }
}

@Composable
private fun SidebarButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun ParticipantHeader(
    activeParticipant: TimeWarpParticipant?,
    audioPlaying: Boolean,
    audioRemaining: Float,
    onPlayAudio: () -> Unit
) {
    Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp), backgroundColor = MD3Colors.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayAudio,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (audioPlaying) VIBRANTColors.warning else VIBRANTColors.info)
            ) {
                Text(if (audioPlaying) "${audioRemaining.roundToInt()}s" else "Timer", color = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(activeParticipant?.displayName ?: "No team loaded", fontWeight = FontWeight.Bold)
                Text(activeParticipant?.utn ?: "UTN unavailable", fontSize = 12.sp, color = MD3Colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RemainingTeamsCard(participants: List<TimeWarpParticipant>, currentIndex: Int) {
    Card(elevation = 4.dp, backgroundColor = MD3Colors.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Remaining (${participants.size})", fontWeight = FontWeight.Bold)
            if (participants.isEmpty()) {
                Text("No teams loaded", color = MD3Colors.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(participants) { participant ->
                        Text(participant.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWarpField(
    flipped: Boolean,
    clickedZones: Set<Int>,
    onZoneClick: (Int) -> Unit,
    zoneEnabled: (Int) -> Boolean
) {
    Card(
        elevation = 6.dp,
        shape = RoundedCornerShape(24.dp),
        backgroundColor = MD3Colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { row ->
                Row(modifier = Modifier.height(if (row == 1) 120.dp else 72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val columns = if (flipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)
                    columns.forEach { column ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MD3Colors.surface)
                                .border(1.dp, MD3Colors.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                column == 0 -> ZoneButton(zoneNumber = 1, clicked = 1 in clickedZones, enabled = zoneEnabled(1), onClick = onZoneClick)
                                column == 1 -> ZoneButton(zoneNumber = 2, clicked = 2 in clickedZones, enabled = zoneEnabled(2), onClick = onZoneClick)
                                column == 2 && row == 1 -> ZoneButton(zoneNumber = 3, clicked = 3 in clickedZones, enabled = zoneEnabled(3), onClick = onZoneClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneButton(zoneNumber: Int, clicked: Boolean, enabled: Boolean, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(zoneNumber) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(backgroundColor = if (clicked) MD3Colors.success else VIBRANTColors.tertiary, contentColor = Color.White),
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Zone $zoneNumber", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlRow(
    onAllRollers: () -> Unit,
    allRollersActive: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onShowTeams: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPrevious, modifier = Modifier.weight(1f)) { Text("Previous") }
        Button(onClick = onAllRollers, colors = ButtonDefaults.buttonColors(if (allRollersActive) MD3Colors.success else MD3Colors.primary), modifier = Modifier.weight(1f)) {
            Text("All Rollers", color = Color.White)
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next") }
        Button(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
        Button(onClick = onShowTeams, modifier = Modifier.weight(1f)) { Text("Teams") }
    }
}

@Composable
private fun FloatingHomeButton(onClick: () -> Unit) {
    androidx.compose.material.IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(16.dp)
            .background(MD3Colors.primary, RoundedCornerShape(24.dp))
    ) {
        androidx.compose.material.Icon(Icons.Default.Home, contentDescription = "Home", tint = MD3Colors.onPrimary)
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmText) }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) }
    )
}

@Composable
private fun AddTeamDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                if (handler.isNotBlank() && dog.isNotBlank()) {
                    onAdd(handler, dog, utn)
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWarpTextField(value = handler, label = "Handler", onValueChange = { handler = it })
                TimeWarpTextField(value = dog, label = "Dog", onValueChange = { dog = it })
                TimeWarpTextField(value = utn, label = "UTN", onValueChange = { utn = it })
            }
        }
    )
}

@Composable
private fun TimePromptDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("Set") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Enter Time Remaining") },
        text = {
            TimeWarpTextField(value = value, label = "Seconds", onValueChange = onValueChange)
        }
    )
}

@Composable
private fun TimeWarpTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        colors = TextFieldDefaults.textFieldColors(backgroundColor = MD3Colors.surfaceContainer)
    )
}

@Composable
private fun TeamModal(participants: List<TimeWarpParticipant>, completed: List<TimeWarpParticipant>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text("All Participants", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Active (${participants.size})", fontWeight = FontWeight.Bold)
                participants.forEach { Text(it.displayName) }
                Spacer(Modifier.height(12.dp))
                Text("Completed (${completed.size})", fontWeight = FontWeight.Bold)
                completed.forEach { participant ->
                    val result = participant.result
                    Text("${participant.displayName} — ${result?.score ?: 0} pts / ${result?.timeRemaining?.formatTime()}s")
                }
            }
        }
    )
}

private data class TimeWarpParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val result: TimeWarpRoundResult? = null
) {
    val displayName: String get() = buildString {
        append(handler.ifBlank { "Unknown Handler" })
        append(" & ")
        append(dog.ifBlank { "Dog" })
    }
}

private data class TimeWarpRoundResult(
    val score: Int,
    val timeRemaining: Float,
    val misses: Int,
    val zonesCaught: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean
)

private data class TimeWarpSnapshot(
    val score: Int,
    val timer: Float,
    val timerRunning: Boolean,
    val misses: Int,
    val ob: Int,
    val clickedZones: Set<Int>,
    val sweetSpot: Boolean,
    val allRollers: Boolean,
    val action: String
)

private fun Float.formatTime(): String = String.format("%02d:%02d", this.toInt() / 60, this.toInt() % 60)

private const val TIMEWARP_HELP_TEXT = "TimeWarp — Button functions\n\n- Timer: plays the 60s timer audio.\n- Export: exports participants.\n- Log: exports run log.\n- Add Team: opens add participant modal.\n- Reset Score: resets scoring and stops timers.\n- Flip Field: toggles field orientation.\n- Zone buttons: mark zones as clicked and update score.\n- Sweet Spot: toggles sweet spot bonus.\n"

private object MD3Colors {
    val primary = Color(0xFF6750A4)
    val onPrimary = Color.White
    val secondary = Color(0xFF625B71)
    val onSecondary = Color.White
    val background = Color(0xFFFFFBFE)
    val surface = Color(0xFFFFFBFE)
    val surfaceContainer = Color(0xFFF5EFF7)
    val surfaceVariant = Color(0xFFE7E0EC)
    val onSurface = Color(0xFF1C1B1F)
    val onSurfaceVariant = Color(0xFF49454F)
    val primaryContainer = Color(0xFFEADDFF)
    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFFB74D)
    val error = Color(0xFFB3261E)
    val outline = Color(0xFF79747E)
    val outlineVariant = Color(0xFFCAC4D0)
    val info = Color(0xFF2196F3)
}

private object VIBRANTColors {
    val primary = Color(0xFF2979FF)
    val warning = Color(0xFFFF9100)
    val info = Color(0xFF00B8D4)
    val tertiary = Color(0xFFF500A1)
}

@Serializable
private data class LogEntry(
    val timestamp: String,
    val action: String,
    val detail: String? = null
)

@Serializable
private data class LogExportPayload(
    val createdAt: String,
    val entries: List<LogEntry>
)

expect suspend fun shareJsonFile(filename: String, content: String)
