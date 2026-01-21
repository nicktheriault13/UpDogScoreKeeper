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
import androidx.compose.material.MaterialTheme
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
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

// Helper for loading template
fun fetchTemplateBytes(@Suppress("UNUSED_PARAMETER") name: String): ByteArray {
    // Stub implementation - in real app use resource loader
    return ByteArray(0)
}

sealed class SevenUpDialogState {
    object None : SevenUpDialogState()
    object ManualTimeEntry : SevenUpDialogState()
    object AddParticipant : SevenUpDialogState()
}

private val primaryBlue = Color(0xFF2979FF)
private val infoCyan = Color(0xFF00B8D4)
private val successGreen = Color(0xFF00C853)
private val boomPink = Color(0xFFF500A1)
private val disabledBackground = Color(0xFFF1F1F1)
private val disabledContent = Color(0xFF222222)

object SevenUpScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SevenUpScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val audioTimerPlaying by screenModel.audioTimerPlaying.collectAsState()
        val audioTimerPosition by screenModel.audioTimerPosition.collectAsState()
        val audioTimerDuration by screenModel.audioTimerDuration.collectAsState()
        val dialogState = remember { mutableStateOf<SevenUpDialogState>(SevenUpDialogState.None) }
        val activeDialog by dialogState
        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents, SevenUpScreenModel.ImportMode.Add)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes, SevenUpScreenModel.ImportMode.Add)
                    else -> {}
                }
            }
        }

        // Separate audio player for the timer sound asset
        val timerAudioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("SevenUp") })

        LaunchedEffect(audioTimerPlaying) {
            if (audioTimerPlaying) {
                timerAudioPlayer.play()
            } else {
                timerAudioPlayer.stop()
            }
        }

        // Manual countdown timer logic is handled in ViewModel, UI just reflects it

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
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
                        SevenUpHeaderCard(
                            navigator = navigator,
                            uiState = uiState,
                            onUndo = { screenModel.undo() },
                            onVersionToggle = { screenModel.toggleGridVersion() },
                            onAllRollers = { screenModel.toggleAllRollers() },
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer only
                        SevenUpTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft.toDouble(),
                            audioTimerPlaying = audioTimerPlaying,
                            audioTimerProgress = if (audioTimerDuration > 0) audioTimerPosition.toFloat() / audioTimerDuration else 0f,
                            audioTimerLeft = ((audioTimerDuration - audioTimerPosition) / 1000).coerceAtLeast(0),
                            onCountdownStart = { screenModel.startCountdown() },
                            onCountdownStop = { screenModel.stopCountdown() },
                            onAudioTimerToggle = { screenModel.toggleAudioTimer() },
                            onLongPressStop = { dialogState.value = SevenUpDialogState.ManualTimeEntry },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Main game grid and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main grid (center)
                        SevenUpGrid(
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
                            SevenUpQueueCard(
                                participants = uiState.queue,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            SevenUpTeamManagementCard(
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    scope.launch {
                                        val template = fetchTemplateBytes("sevenup")
                                        screenModel.exportData(template)
                                    }
                                },
                                onLog = { screenModel.exportLog() },
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
                                onClick = { screenModel.previousParticipant() },
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
                                onClick = { screenModel.nextParticipant() },
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
                                onClick = { screenModel.skipParticipant() },
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

        when (activeDialog) {
            is SevenUpDialogState.ManualTimeEntry -> {
                ManualTimeDialog(
                    onConfirm = { time ->
                        screenModel.setManualTime(time)
                        dialogState.value = SevenUpDialogState.None
                    },
                    onDismiss = { dialogState.value = SevenUpDialogState.None }
                )
            }
            is SevenUpDialogState.AddParticipant -> {
                AddParticipantDialog(
                    onAdd = { h, d, u ->
                        screenModel.addParticipant(h, d, u)
                        dialogState.value = SevenUpDialogState.None
                    },
                    onDismiss = { dialogState.value = SevenUpDialogState.None }
                )
            }
            else -> {}
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

        if (showResetRoundDialog) {
            AlertDialog(
                onDismissRequest = { showResetRoundDialog = false },
                title = { Text("Reset Round?") },
                text = { Text("Are you sure you want to reset the current round? All scores will be lost. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.resetCurrentRound()
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
fun SevenUpHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    uiState: SevenUpUiState,
    onUndo: () -> Unit,
    onVersionToggle: () -> Unit,
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
                            text = "Seven Up",
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
                    Text("Jumps:", fontSize = 14.sp)
                    Text("${uiState.jumpsClickedCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Non-Jumps:", fontSize = 14.sp)
                    Text("${uiState.nonJumpsClickedCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Version and All Rollers buttons in same row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Version toggle button
                    Button(
                        onClick = onVersionToggle,
                        enabled = !uiState.hasStarted,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF6750A4),
                            contentColor = Color.White,
                            disabledBackgroundColor = Color(0xFF9E9E9E),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("V ${uiState.gridVersion + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // All Rollers button
                    Button(
                        onClick = onAllRollers,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (uiState.allRollers) Color(0xFF00C853) else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Column 3: Score and Sweet Spot indicator
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
                            text = uiState.currentScore.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                // Sweet Spot Bonus indicator
                if (uiState.sweetSpotBonusActive) {
                    Card(
                        backgroundColor = boomPink,
                        elevation = 2.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            "SWEET SPOT!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SevenUpGrid(
    screenModel: SevenUpScreenModel,
    uiState: SevenUpUiState,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), elevation = 8.dp) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Calculate grid implementation based on Boom's logic
            val cols = 5
            val rows = 3
            val spacing = 8.dp

            // Map grid based on version and flip state
            val layout = getSevenUpLayout(uiState.gridVersion, uiState.isFieldFlipped)

            val roundOver = uiState.nonJumpMark > 5

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                (0 until rows).forEach { r ->
                    Row(
                        modifier = Modifier.weight(if (r == 1) 2f else 1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        (0 until cols).forEach { c ->
                            val cellType = layout[r][c]

                            val cellKey = "$r,$c"
                            val nonJumpMarkValue = uiState.markedCells[cellKey]

                            // Cell implementation
                            val isActive = when (cellType) {
                                is SevenUpCell.Jump -> uiState.jumpState[cellType.id]?.clicked == true
                                is SevenUpCell.NonJump -> nonJumpMarkValue != null
                                else -> false
                            }

                            val isDisabled = when {
                                roundOver -> true
                                cellType is SevenUpCell.Jump -> uiState.jumpState[cellType.id]?.disabled == true
                                cellType is SevenUpCell.NonJump -> uiState.nonJumpDisabled || nonJumpMarkValue != null
                                else -> true
                            }

                            val jumpCount = when (cellType) {
                                is SevenUpCell.Jump -> uiState.jumpCounts[cellType.id] ?: 0
                                else -> null
                            }

                            val nonJumpLabel = when (cellType) {
                                is SevenUpCell.NonJump -> nonJumpMarkValue?.toString()
                                else -> null
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (cellType !is SevenUpCell.Empty) {
                                    SevenUpButton(
                                        label = cellType.label,
                                        count = jumpCount,
                                        nonJumpLabel = nonJumpLabel,
                                        active = isActive,
                                        disabled = isDisabled && !isActive,
                                        isSweetSpot = cellType is SevenUpCell.NonJump && cellType.isSweetSpot,
                                        onClick = { if (!roundOver) screenModel.handleGridClick(r, c, cellType) }
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

@Composable
fun SevenUpButton(
    label: String,
    count: Int?,
    nonJumpLabel: String?,
    active: Boolean,
    disabled: Boolean,
    isSweetSpot: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        active -> if (isSweetSpot) boomPink else successGreen
        disabled -> disabledBackground
        isSweetSpot -> infoCyan
        else -> primaryBlue
    }

    Button(
        onClick = onClick,
        enabled = !disabled || active,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = bgColor,
            contentColor = if (active || !disabled) Color.White else disabledContent,
            disabledBackgroundColor = disabledBackground,
            disabledContentColor = disabledContent
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)

            when {
                // Jump counter
                count != null -> {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                // Non-jump mark label (1..5)
                nonJumpLabel != null -> {
                    Text(
                        text = nonJumpLabel,
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SevenUpTimerCard(
    timerRunning: Boolean,
    timeLeft: Double,
    audioTimerPlaying: Boolean,
    audioTimerProgress: Float,
    audioTimerLeft: Long,
    onCountdownStart: () -> Unit,
    onCountdownStop: () -> Unit,
    onAudioTimerToggle: () -> Unit,
    onLongPressStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: START TIMER and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCountdownStart,
                    enabled = !timerRunning,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFF9E9E9E),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶", fontSize = 16.sp)
                        Text("TIMER", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = onCountdownStop,
                    enabled = timerRunning,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFF9E9E9E),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏸", fontSize = 16.sp)
                        Text("PAUSE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Middle row: EDIT and AUDIO TIMER buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onLongPressStop,
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
                    onClick = onAudioTimerToggle,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (audioTimerPlaying) Color(0xFFFF9100) else Color(0xFF00BCD4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (audioTimerPlaying) "⏹" else "▶", fontSize = 16.sp)
                        Text("AUDIO", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Time Remaining display
            Text(
                "Time: ${timeLeft.toString().take(5)}s",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (audioTimerPlaying) {
                Text(
                    "Audio: ${audioTimerLeft}s",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ... Additional helper composables (Queue, ImportExport, ControlRow) would follow standard patterns ...
// Using placeholders for brevity in this fix to ensure compilation

@Composable
fun SevenUpQueueCard(
    participants: List<SevenUpParticipant>,
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
fun SevenUpTeamManagementCard(
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

// Data Classes for UI State (Assuming ScreenModel is updated to support this)
// These should ideally be in SevenUpScreenModel.kt, but defining minimal structures here if needed for Preview/Compilation check
// NOTE: Actual ScreenModel implementation must provide these.

// SevenUpCell moved to SevenUpScreenModel.kt

fun getSevenUpLayout(version: Int, flipped: Boolean): List<List<SevenUpCell>> {
    // Grid: 3 rows x 5 cols.
    // Matches the React code's per-version mapping. Empty cells are NonJump(""), Sweet Spot is NonJump("SS", true).

    fun base(): List<List<SevenUpCell>> {
        val E = SevenUpCell.NonJump("")
        val SS = SevenUpCell.NonJump("SS", true)
        fun J(n: Int) = SevenUpCell.Jump("J$n", "Jump$n")

        return when (version) {
            0 -> listOf(
                listOf(E, J(3), E, E, J(7)),
                listOf(J(1), E, SS, J(5), E),
                listOf(J(2), J(4), E, J(6), E)
            )
            1 -> listOf(
                listOf(J(1), E, E, E, J(7)),
                listOf(J(2), J(3), SS, J(5), E),
                listOf(E, E, J(4), J(6), E)
            )
            2 -> listOf(
                listOf(J(1), J(3), E, J(4), E),
                listOf(J(2), E, SS, E, J(6)),
                listOf(E, E, E, J(5), J(7))
            )
            3 -> listOf(
                listOf(E, J(2), E, E, E),
                listOf(J(1), J(3), SS, J(5), J(6)),
                listOf(E, E, J(4), E, J(7))
            )
            4 -> listOf(
                listOf(J(1), E, J(4), E, J(6)),
                listOf(E, J(3), SS, J(5), E),
                listOf(J(2), E, E, E, J(7))
            )
            5 -> listOf(
                listOf(E, E, J(4), E, E),
                listOf(E, J(2), SS, J(5), E),
                listOf(J(1), J(3), E, J(6), J(7))
            )
            6 -> listOf(
                listOf(E, J(2), E, J(5), J(6)),
                listOf(E, J(3), SS, E, J(7)),
                listOf(J(1), J(4), E, E, E)
            )
            7 -> listOf(
                listOf(J(1), E, E, SevenUpCell.Empty, SevenUpCell.Empty),
                listOf(E, J(3), SS, SevenUpCell.Empty, SevenUpCell.Empty),
                listOf(J(2), E, E, SevenUpCell.Empty, SevenUpCell.Empty)
            )
            8 -> listOf(
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, J(1), E, E),
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, SS, J(2), E),
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, E, E, J(3))
            )
            9 -> listOf(
                listOf(E, J(1), E, SevenUpCell.Empty, SevenUpCell.Empty),
                listOf(E, J(2), SS, SevenUpCell.Empty, SevenUpCell.Empty),
                listOf(E, J(3), E, SevenUpCell.Empty, SevenUpCell.Empty)
            )
            10 -> listOf(
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, E, E, E),
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, SS, J(2), J(3)),
                listOf(SevenUpCell.Empty, SevenUpCell.Empty, J(1), E, J(4))
            )
            else -> listOf(
                listOf(E, J(3), E, E, J(7)),
                listOf(J(1), E, SS, J(5), E),
                listOf(J(2), J(4), E, J(6), E)
            )
        }
    }

    val raw = base()

    // React applies column reversal for flipped depending on mapping; our earlier implementation reversed rows+cols.
    // To match the existing app behavior, we keep the same full flip (reverse both axes).
    return if (flipped) raw.reversed().map { it.reversed() } else raw
}

// Dialog Composable Placeholders
@Composable
fun ManualTimeDialog(onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Time") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Seconds") }) },
        confirmButton = { Button(onClick = { text.toDoubleOrNull()?.let(onConfirm) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddParticipantDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = { Column {
            OutlinedTextField(value = h, onValueChange = { h = it }, label = { Text("Handler") })
            OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Dog") })
            OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("UTN") })
        }},
        confirmButton = { Button(onClick = { onAdd(h, d, u) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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

// Stub for Missing ScreenModel/State classes if they don't exist yet
// Assuming SevenUpScreenModel exists and returns SevenUpUiState

