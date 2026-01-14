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
        val remainingTeams = state.participants.filterNot { it.hasScoringData() }

        var textPreview by remember { mutableStateOf<TextPreview?>(null) }
        var showLogDialog by remember { mutableStateOf(false) }
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
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Far Out") })
        val timerDisplay = state.timerDisplay

        LaunchedEffect(timerDisplay.isRunning, timerDisplay.isPaused) {
            when {
                !timerDisplay.isRunning -> audioPlayer.stop()
                timerDisplay.isPaused -> audioPlayer.pause()
                else -> audioPlayer.play()
            }
        }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Home button is rendered inside the header to avoid overlap.

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    FarOutHeader(
                        navigator = navigator,
                        activeName = activeParticipant?.displayName().orEmpty().ifBlank { "No team loaded" },
                        score = state.score,
                        timer = state.timerDisplay,
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
                        onBack = {
                            logButtonPress("Back")
                            navigator.pop()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FarOutSidebar(
                            timerLabel = state.timerDisplay.secondsRemaining,
                            onImport = {
                                logButtonPress("Import")
                                filePicker.launch()
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
                            onExportLog = {
                                scope.launch {
                                    logButtonPress("Export Log")
                                    val logContent = gameLogger.getLogContents()
                                    val json = Json.encodeToString(logContent)
                                    fileExporter.save("FarOutLog.json", json.encodeToByteArray())
                                }
                            },
                            onAdd = {
                                logButtonPress("Add Team")
                                screenModel.showAddParticipant(true)
                            },
                            onHelp = {
                                logButtonPress("Help")
                                screenModel.toggleHelp(true)
                            },
                            onShowTeams = {
                                logButtonPress("Show Teams")
                                screenModel.showTeamModal(true)
                            },
                            onNext = {
                                logButtonPress("Next")
                                screenModel.nextParticipant(autoExport = true)
                            },
                            onPrev = {
                                logButtonPress("Previous")
                                screenModel.previousParticipant()
                            },
                            onSkip = {
                                logButtonPress("Skip")
                                screenModel.skipParticipant()
                            },
                            onUndo = {
                                logButtonPress("Undo")
                                screenModel.undo()
                            },
                            onResetRound = {
                                logButtonPress("Reset Round")
                                screenModel.resetRound()
                            },
                            onClearParticipants = {
                                logButtonPress("Clear Participants")
                                screenModel.showClearPrompt(true)
                            },
                            onViewLog = {
                                logButtonPress("View Log")
                                showLogDialog = true
                            },
                            undoEnabled = state.undoAvailable
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ParticipantsPanel(
                                    remainingTeams = remainingTeams,
                                    activeParticipant = activeParticipant,
                                    modifier = Modifier.weight(0.35f)
                                )
                                ThrowsCard(
                                    state = state,
                                    onValueChange = screenModel::updateThrow,
                                    onMissToggle = screenModel::toggleMiss,
                                    onDeclinedToggle = screenModel::toggleDeclined,
                                    onAllRollers = screenModel::toggleAllRollers,
                                    onResetRound = screenModel::resetRound,
                                    modifier = Modifier.weight(0.65f),
                                    log = { logButtonPress(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LogCard(entries = state.logEntries.take(6))
                        }
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
    }
}

@Composable
private fun FarOutHeader(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    activeName: String,
    score: Double,
    timer: TimerDisplay,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onBack: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GameHomeButton(navigator = navigator)
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Current Team", style = MaterialTheme.typography.caption)
                Text(text = activeName, style = MaterialTheme.typography.h6, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Score", style = MaterialTheme.typography.caption)
                Text(score.formatScore(), style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            }
            TimerControls(timer, onStartTimer, onPauseTimer, onStopTimer)
        }
    }
}

@Composable
private fun TimerControls(
    timer: TimerDisplay,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "${timer.secondsRemaining}s", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, colors = ButtonDefaults.buttonColors(backgroundColor = Palette.info, contentColor = Palette.onInfo)) {
                Text(if (timer.isRunning) "Restart" else "Start")
            }
            Button(
                onClick = onPause,
                enabled = timer.isRunning,
                colors = ButtonDefaults.buttonColors(backgroundColor = Palette.warning, contentColor = Palette.onWarning)
            ) {
                Text(if (timer.isPaused) "Resume" else "Pause")
            }
            Button(
                onClick = onStop,
                enabled = timer.isRunning || timer.secondsRemaining != 90,
                colors = ButtonDefaults.buttonColors(backgroundColor = Palette.error, contentColor = Palette.onError)
            ) {
                Text("Stop")
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
private fun ParticipantsPanel(
    remainingTeams: List<FarOutParticipant>,
    activeParticipant: FarOutParticipant?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        backgroundColor = Palette.surfaceContainer,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Remaining (${remainingTeams.size})", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            if (remainingTeams.isEmpty()) {
                Text("All teams completed", color = Palette.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(remainingTeams) { participant ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(participant.handler.ifBlank { "Unknown" }, fontWeight = FontWeight.Bold)
                            Text(participant.dog.ifBlank { "No dog" }, style = MaterialTheme.typography.caption)
                            if (participant == activeParticipant) {
                                Text("Active", color = Palette.info, fontSize = 12.sp)
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThrowsCard(
    state: FarOutState,
    onValueChange: (String, String) -> Unit,
    onMissToggle: (String) -> Unit,
    onDeclinedToggle: () -> Unit,
    onAllRollers: () -> Unit,
    onResetRound: () -> Unit,
    modifier: Modifier = Modifier,
    log: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        backgroundColor = Palette.surfaceContainer,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { 
                        log("All Rollers button pressed")
                        onAllRollers() 
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (state.allRollersPressed) Palette.success else Palette.primary,
                        contentColor = if (state.allRollersPressed) Palette.onSuccess else Palette.onPrimary
                    )
                ) {
                    Text(if (state.allRollersPressed) "All Rollers ✓" else "All Rollers")
                }
                OutlinedButton(onClick = { 
                    log("Reset Score button pressed")
                    onResetRound() 
                }) {
                    Text("Reset Score")
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Score", style = MaterialTheme.typography.caption)
                    Text(text = state.score.formatScore(), style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
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
