package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.logging.rememberGameLogger
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.*
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import com.ddsk.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object FarOutScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FarOutScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val state by screenModel.state.collectAsState()
        val activeParticipant = state.participants.getOrNull(state.activeIndex)
        // Queue shows only teams without score data (not yet completed)
        val queueParticipants = state.participants.filter { participant ->
            participant.throw1.isBlank() &&
            participant.throw2.isBlank() &&
            participant.throw3.isBlank() &&
            participant.sweetShot.isBlank() &&
            participant.score == 0.0
        }

        var textPreview by remember { mutableStateOf<TextPreview?>(null) }
        var showLogDialog by remember { mutableStateOf(false) }
        var showImportModeDialog by remember { mutableStateOf(false) }
        var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        val gameLogger = rememberGameLogger("FarOut")
        val assetLoader = rememberAssetLoader()

        // Helper to log button presses with context
        fun logButtonPress(buttonName: String) {
            val teamName = activeParticipant?.displayName() ?: "None"
            val score = state.score
            gameLogger.log("Button: $buttonName | Team: $teamName | Score: $score")
        }

        val fileExporter = rememberFileExporter()
        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImportResult = result
                    showImportModeDialog = true
                }
                else -> {}
            }
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Far Out") })
        val currentAudioTime by audioPlayer.currentTime.collectAsState()
        val audioRemainingSeconds = remember(audioPlayer.duration, currentAudioTime) {
            ((audioPlayer.duration - currentAudioTime) / 1000).coerceAtLeast(0)
        }
        val timerDisplay = state.timerDisplay

        LaunchedEffect(timerDisplay.isRunning, timerDisplay.isPaused) {
            when {
                !timerDisplay.isRunning -> audioPlayer.stop()
                timerDisplay.isPaused -> audioPlayer.pause()
                else -> audioPlayer.play()
            }
        }

        // Handle JSON export via saveJsonFileWithPicker (shares on Android, saves on Desktop)
        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        FarOutHeaderCard(
                            navigator = navigator,
                            activeName = activeParticipant?.displayName().orEmpty().ifBlank { "No team loaded" },
                            score = state.score,
                            onUndo = {
                                logButtonPress("Undo")
                                screenModel.undo()
                            },
                            undoEnabled = state.undoAvailable,
                            allRollersPressed = state.allRollersPressed,
                            onAllRollers = {
                                logButtonPress("All Rollers")
                                screenModel.toggleAllRollers()
                            },
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer only
                        FarOutTimerCard(
                            timer = state.timerDisplay,
                            timeLeft = audioRemainingSeconds,
                            onStartTimer = {
                                logButtonPress("Start Timer")
                                screenModel.startTimer()
                            },
                            onPauseTimer = {
                                logButtonPress("Pause Timer")
                                screenModel.pauseOrResumeTimer()
                            },
                            onStopTimer = {
                                logButtonPress("Stop Timer")
                                screenModel.stopTimer()
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Scoring area and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Scoring area (center) - replaces the grid
                        FarOutScoringCard(
                            state = state,
                            onValueChange = screenModel::updateThrow,
                            onMissToggle = screenModel::toggleMiss,
                            onDeclinedToggle = screenModel::toggleDeclined,
                            log = { logButtonPress(it) },
                            modifier = Modifier.weight(1f)
                        )

                        // Right side: Queue and Team Management
                        Column(
                            modifier = Modifier.weight(0.3f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Queue card showing teams
                            FarOutQueueCard(
                                remainingTeams = queueParticipants,
                                activeParticipant = activeParticipant,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            FarOutTeamManagementCard(
                                onClearTeams = {
                                    logButtonPress("Clear Participants")
                                    screenModel.showClearPrompt(true)
                                },
                                onImport = {
                                    logButtonPress("Import")
                                    filePicker.launch()
                                },
                                onAddTeam = {
                                    logButtonPress("Add Team")
                                    screenModel.showAddParticipant(true)
                                },
                                onExport = {
                                    scope.launch {
                                        logButtonPress("Export")
                                        val templatePath = "templates/UDC FarOut Data Entry L1 Div Sort 1-2025.xlsm"
                                        val templateBytes = try {
                                            assetLoader.load(templatePath)
                                        } catch (e: Exception) {
                                            null
                                        }

                                        val result = try {
                                            screenModel.exportParticipantsAsXlsx(templateBytes)
                                        } catch (e: Exception) {
                                            gameLogger.log("Export exception: ${e.message}")
                                            null
                                        }

                                        if (result != null) {
                                            fileExporter.save(result.fileName, result.data)
                                            gameLogger.log("Export initiated: ${result.fileName}")
                                        } else {
                                            gameLogger.log("Export failed: missing template or data generation error")
                                        }
                                    }
                                },
                                onLog = {
                                    scope.launch {
                                        logButtonPress("Export Log")
                                        val logContent = gameLogger.getLogContents()
                                        val json = Json.encodeToString(logContent)
                                        fileExporter.save("FarOutLog.json", json.encodeToByteArray())
                                    }
                                },
                                onResetRound = {
                                    logButtonPress("Reset Round")
                                    showResetRoundDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Bottom row: Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Navigation buttons below the scoring area
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    logButtonPress("Help")
                                    screenModel.toggleHelp(true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("? HELP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = {
                                    logButtonPress("Previous")
                                    screenModel.previousParticipant()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("◄◄ PREV", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = {
                                    logButtonPress("Next")
                                    screenModel.nextParticipant(autoExport = true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("► NEXT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = {
                                    logButtonPress("Skip")
                                    screenModel.skipParticipant()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("►► SKIP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Empty spacer to align with right column
                        Spacer(modifier = Modifier.weight(0.3f))
                    }
                }
            }
        }

        if (state.showAddParticipant) {
            AddParticipantDialog(
                onDismiss = { screenModel.showAddParticipant(false) },
                onConfirm = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    screenModel.showAddParticipant(false)
                }
            )
        }

        if (state.showHelp) {
            TextPreviewDialog(
                title = "Help — Button Functions",
                payload = state.helpText,
                onDismiss = { screenModel.toggleHelp(false) }
            )
        }

        if (state.showTeamModal) {
            TeamListDialog(
                participants = state.participants,
                onDismiss = { screenModel.showTeamModal(false) }
            )
        }

        if (state.showClearPrompt) {
            ConfirmDialog(
                title = "Clear All Participants?",
                message = "This removes every team from the queue. This cannot be undone.",
                onDismiss = { screenModel.showClearPrompt(false) },
                onConfirm = {
                    screenModel.clearParticipants()
                    screenModel.showClearPrompt(false)
                }
            )
        }

        if (textPreview != null) {
            val preview = textPreview
            if (preview != null) {
                TextPreviewDialog(
                    title = preview.title,
                    payload = preview.payload,
                    onDismiss = { textPreview = null }
                )
            }
        }

        if (showLogDialog) {
            LogDialog(entries = state.logEntries, onDismiss = { showLogDialog = false }) { 
                gameLogger.getLogContents()
            }
        }

        if (state.tieWarning.visible) {
            ConfirmDialog(
                title = "Unresolved Tie",
                message = state.tieWarning.message,
                confirmLabel = "Ok",
                showDismiss = false,
                onDismiss = { screenModel.resolveTieWarning(null) },
                onConfirm = { screenModel.resolveTieWarning(null) }
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
                                screenModel.importParticipantsFromCsv(import.contents, FarOutScreenModel.ImportMode.Add)
                            } else if (import is ImportResult.Xlsx) {
                                screenModel.importParticipantsFromXlsx(import.bytes, FarOutScreenModel.ImportMode.Add)
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
                                    screenModel.importParticipantsFromCsv(import.contents, FarOutScreenModel.ImportMode.ReplaceAll)
                                } else if (import is ImportResult.Xlsx) {
                                    screenModel.importParticipantsFromXlsx(import.bytes, FarOutScreenModel.ImportMode.ReplaceAll)
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
                            screenModel.resetRound()
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
private fun FarOutHeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    activeName: String,
    score: Double,
    onUndo: () -> Unit,
    undoEnabled: Boolean,
    allRollersPressed: Boolean,
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
                            text = "Far Out",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = activeName,
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onUndo,
                    enabled = undoEnabled,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFD50000),
                        contentColor = Color.White,
                        disabledBackgroundColor = Color(0xFF9E9E9E),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier.width(100.dp).height(42.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Column 2: Stats placeholder
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Space for future stats if needed
            }

            // Column 3: Score display and All Rollers button
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Score: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = score.formatScore(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Button(
                    onClick = onAllRollers,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (allRollersPressed) Color(0xFF00C853) else Color(0xFF6750A4),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (allRollersPressed) "ALL ROLLERS ✓" else "ALL ROLLERS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FarOutTimerCard(
    timer: TimerDisplay,
    timeLeft: Int,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onStopTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: START and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartTimer,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶", fontSize = 16.sp)
                        Text(if (timer.isRunning) "RESTART" else "START", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = onPauseTimer,
                    enabled = timer.isRunning,
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
                        Text(if (timer.isPaused) "▶" else "⏸", fontSize = 16.sp)
                        Text(if (timer.isPaused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                    text = if (timer.isRunning) "${timeLeft}s" else "Ready",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer.isRunning) Color(0xFF00BCD4) else Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                if (timer.isRunning) {
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
private fun FarOutSidebar(
    timerLabel: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportLog: () -> Unit,
    onAdd: () -> Unit,
    onHelp: () -> Unit,
    onShowTeams: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onResetRound: () -> Unit,
    onClearParticipants: () -> Unit,
    onViewLog: () -> Unit,
    undoEnabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 6.dp,
        backgroundColor = Palette.surfaceContainer,
        modifier = Modifier.width(220.dp).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SidebarButton(label = "Import", color = Palette.info, onClick = onImport)
            SidebarButton(label = "Export", color = Palette.info, onClick = onExport)
            SidebarButton(label = "Export Log", color = Palette.info, onClick = onExportLog)
            SidebarButton(label = "Add Team", color = Palette.primary, onClick = onAdd)
            SidebarButton(label = "Help", color = Palette.primaryContainer, onClick = onHelp, contentColor = Palette.onPrimaryContainer)
            Divider()
            SidebarButton(label = "View Log", color = Palette.surface, contentColor = Palette.onSurface, onClick = onViewLog)
            SidebarButton(label = "Teams (${timerLabel}s)", color = Palette.surface, contentColor = Palette.onSurface, onClick = onShowTeams)
            SidebarButton(label = "Undo", color = Palette.surface, contentColor = Palette.onSurface, onClick = onUndo, enabled = undoEnabled)
            Divider()
            SidebarButton(label = "Previous", color = Palette.primary, onClick = onPrev)
            SidebarButton(label = "Skip", color = Palette.warning, contentColor = Palette.onWarning, onClick = onSkip)
            SidebarButton(label = "Next", color = Palette.success, contentColor = Palette.onSuccess, onClick = onNext)
            SidebarButton(label = "Reset Round", color = Palette.error, contentColor = Palette.onError, onClick = onResetRound)
            SidebarButton(label = "Clear Teams", color = Color(0xFFD50000), contentColor = Palette.onError, onClick = onClearParticipants)
        }
    }
}

@Composable
private fun SidebarButton(
    label: String,
    color: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = contentColor)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FarOutQueueCard(
    remainingTeams: List<FarOutParticipant>,
    activeParticipant: FarOutParticipant?,
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
                "Queue (${remainingTeams.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Display teams in queue - scrollable list that fills available space
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (remainingTeams.isEmpty()) {
                    item {
                        Text(
                            "All teams completed",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(remainingTeams) { participant ->
                        val isActive = participant == activeParticipant
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = if (isActive) Color(0xFFE3F2FD) else Color.White,
                            elevation = if (isActive) 2.dp else 0.dp,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isActive) "▶ ${participant.displayName()}"
                                           else participant.displayName(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) Color(0xFF1976D2) else Color.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
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
private fun FarOutTeamManagementCard(
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
private fun FarOutScoringCard(
    state: FarOutState,
    onValueChange: (String, String) -> Unit,
    onMissToggle: (String) -> Unit,
    onDeclinedToggle: () -> Unit,
    modifier: Modifier = Modifier,
    log: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThrowRow(
                label = "Throw 1",
                value = state.throwInputs.throw1,
                isMiss = state.missStates.throw1Miss,
                onValue = { onValueChange("Throw 1", it) },
                onMiss = { 
                    log("Throw 1 Miss button pressed")
                    onMissToggle("Throw 1") 
                }
            )
            ThrowRow(
                label = "Throw 2",
                value = state.throwInputs.throw2,
                isMiss = state.missStates.throw2Miss,
                onValue = { onValueChange("Throw 2", it) },
                onMiss = { 
                    log("Throw 2 Miss button pressed")
                    onMissToggle("Throw 2") 
                }
            )
            ThrowRow(
                label = "Throw 3",
                value = state.throwInputs.throw3,
                isMiss = state.missStates.throw3Miss,
                onValue = { onValueChange("Throw 3", it) },
                onMiss = { 
                    log("Throw 3 Miss button pressed")
                    onMissToggle("Throw 3") 
                }
            )
            SweetShotRow(
                value = state.throwInputs.sweetShot,
                isMiss = state.missStates.sweetShotMiss,
                declined = state.sweetShotDeclined,
                onValue = { onValueChange("Sweet Shot", it) },
                onMiss = { 
                    log("Sweet Shot Miss button pressed")
                    onMissToggle("Sweet Shot") 
                },
                onDeclined = { 
                    log("Sweet Shot Decline button pressed")
                    onDeclinedToggle() 
                },
                enabled = !state.sweetShotDeclined
            )

            Spacer(modifier = Modifier.weight(1f))

            Divider()

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Score", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = state.score.formatScore(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ThrowRow(
    label: String,
    value: String,
    isMiss: Boolean,
    onValue: (String) -> Unit,
    onMiss: () -> Unit
) {
    Column {
        Text(label, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isMiss,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            MissButton(isMiss = isMiss, onClick = onMiss)
        }
    }
}

@Composable
private fun SweetShotRow(
    value: String,
    isMiss: Boolean,
    declined: Boolean,
    onValue: (String) -> Unit,
    onMiss: () -> Unit,
    onDeclined: () -> Unit,
    enabled: Boolean
) {
    Column {
        Text("Sweet Shot", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled && !isMiss,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            MissButton(isMiss = isMiss, onClick = onMiss, enabled = !declined)
            OutlinedButton(
                onClick = onDeclined,
                border = BorderStroke(1.dp, Palette.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = if (declined) Palette.error else Color.Transparent,
                    contentColor = if (declined) Palette.onError else Palette.error
                )
            ) {
                Text(if (declined) "Declined" else "Decline")
            }
        }
    }
}

@Composable
private fun MissButton(isMiss: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, Palette.error),
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = if (isMiss) Palette.error else Color.Transparent,
            contentColor = if (isMiss) Palette.onError else Palette.error
        )
    ) {
        Text("Miss")
    }
}

@Composable
private fun LogCard(entries: List<FarOutLogEntry>) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text("Recent Log", fontWeight = FontWeight.Bold)
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            if (entries.isEmpty()) {
                Text("No log entries yet", color = Palette.onSurfaceVariant)
            } else {
                entries.forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(entry.event, fontWeight = FontWeight.SemiBold)
                        Text(entry.team, style = MaterialTheme.typography.caption)
                        Text(entry.timestamp, style = MaterialTheme.typography.caption, color = Palette.onSurfaceVariant)
                    }
                    Divider()
                }
            }
        }
    }
}

// ImportDialog removed

@Composable
private fun AddParticipantDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Participant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") })
                TextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") })
                TextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") })
            }
        },
        confirmButton = {
            Button(onClick = { if (handler.isNotBlank() || dog.isNotBlank()) onConfirm(handler.trim(), dog.trim(), utn.trim()) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TextPreviewDialog(title: String, payload: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.heightIn(max = 320.dp).width(400.dp).background(Palette.surface).padding(8.dp).border(1.dp, Palette.outlineVariant)) {
                Text(payload)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun LogDialog(
    entries: List<FarOutLogEntry>,
    onDismiss: () -> Unit,
    getLogContent: () -> String
) {
    var logContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        logContent = getLogContent()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Full Run Log") },
        text = {
            Column(modifier = Modifier.height(360.dp).width(420.dp)) {
                LazyColumn {
                    item {
                        Text(logContent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun TeamListDialog(participants: List<FarOutParticipant>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All Participants (${participants.size})") },
        text = {
            Column(modifier = Modifier.height(360.dp).width(420.dp)) {
                if (participants.isEmpty()) {
                    Text("No teams loaded", color = Palette.onSurfaceVariant)
                } else {
                    LazyColumn {
                        items(participants) { participant ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(participant.displayName(), fontWeight = FontWeight.Bold)
                                Text("UTN: ${participant.utn}", style = MaterialTheme.typography.caption)
                                Text("Score: ${participant.score.formatScore()}", style = MaterialTheme.typography.caption)
                            }
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Confirm",
    showDismiss: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = if (showDismiss) {
            {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        } else null
    )
}

private data class TextPreview(val title: String, val payload: String)

private fun FarOutParticipant.displayName(): String = buildString {
    if (handler.isNotBlank()) append(handler)
    if (dog.isNotBlank()) {
        if (isNotEmpty()) append(" & ")
        append(dog)
    }
    if (isBlank()) append("Unknown Team")
}

private fun FarOutParticipant.hasScoringData(): Boolean {
    return throw1.isNotBlank() || throw2.isNotBlank() || throw3.isNotBlank() || sweetShot.isNotBlank() || score > 0
}

private fun Double.formatScore(): String = if (this % 1.0 == 0.0) "${this.toInt()}" else "$this"
