package com.ddsk.app.ui.screens.games

// removed imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.games.ui.GameHomeOverlay
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

// Helper for loading template
fun fetchTemplateBytes(name: String): ByteArray {
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
private val warningOrange = Color(0xFFFF9100)
private val boomPink = Color(0xFFF500A1)
private val errorRed = Color(0xFFD50000)
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

        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                GameHomeOverlay(navigator = navigator)
                val columnSpacing = 16.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(columnSpacing),
                    horizontalArrangement = Arrangement.spacedBy(columnSpacing)
                ) {
                    // Left Column: Score, Grid, Controls
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        SevenUpScoreSummaryCard(uiState)
                        Box(modifier = Modifier.weight(1f)) {
                            SevenUpGrid(
                                screenModel = screenModel,
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SevenUpControlRow(screenModel = screenModel, uiState = uiState)
                    }

                    // Right Column: Timers, Queue, Import/Export
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        SevenUpTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft.toDouble(),
                            audioTimerPlaying = audioTimerPlaying,
                            audioTimerProgress = if (audioTimerDuration > 0) audioTimerPosition.toFloat() / audioTimerDuration else 0f,
                            audioTimerLeft = ((audioTimerDuration - audioTimerPosition) / 1000).coerceAtLeast(0),
                            onCountdownStart = { screenModel.startCountdown() },
                            onCountdownStop = { screenModel.stopCountdown() },
                            onAudioTimerToggle = { screenModel.toggleAudioTimer() },
                            onLongPressStop = { dialogState.value = SevenUpDialogState.ManualTimeEntry }
                        )
                        SevenUpQueueCard(uiState = uiState)
                        SevenUpImportExportCard(
                            onImportClick = { filePicker.launch() },
                            onExportClick = {
                                scope.launch {
                                    val template = fetchTemplateBytes("sevenup") // Implementation via FileUtils
                                    screenModel.exportData(template)
                                }
                            },
                            onLogClick = { screenModel.exportLog() }
                        )
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
    }
}

@Composable
fun SevenUpScoreSummaryCard(uiState: SevenUpUiState) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Score: ${uiState.currentScore}", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Jumps: ${uiState.jumpsClickedCount}")
                Text("Non-Jumps: ${uiState.nonJumpsClickedCount}")
                if (uiState.sweetSpotBonusActive) {
                    Text("Sweet Spot Bonus!", color = boomPink, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val active = uiState.activeParticipant
            Text(
                text = active?.let { "Active: ${it.handler} & ${it.dog}" } ?: "No active team",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
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

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                (0 until rows).forEach { r ->
                    Row(
                        modifier = Modifier.weight(if (r == 1) 2f else 1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        (0 until cols).forEach { c ->
                            val cellType = layout[r][c]
                            // Cell implementation
                            val isActive = when (cellType) {
                                is SevenUpCell.Jump -> uiState.jumpState[cellType.id]?.clicked == true
                                is SevenUpCell.NonJump -> uiState.nonJumpState["${r},${c}"]?.clicked == true
                                else -> false
                            }
                            val isDisabled = when (cellType) {
                                is SevenUpCell.Jump -> uiState.jumpState[cellType.id]?.disabled == true
                                is SevenUpCell.NonJump -> uiState.nonJumpDisabled
                                else -> true
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (cellType !is SevenUpCell.Empty) {
                                    SevenUpButton(
                                        label = cellType.label,
                                        active = isActive,
                                        disabled = isDisabled && !isActive,
                                        isSweetSpot = cellType is SevenUpCell.NonJump && cellType.isSweetSpot,
                                        onClick = { screenModel.handleGridClick(r, c, cellType) }
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
    active: Boolean,
    disabled: Boolean,
    isSweetSpot: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        active -> if (isSweetSpot) boomPink else successGreen
        disabled -> disabledBackground
        isSweetSpot -> infoCyan // Special color for Sweet Spot center
        else -> primaryBlue
    }

    Button(
        onClick = onClick,
        enabled = !disabled || active, // Allow clicking active to potentially undo or just valid state? Logic handled in Model
        colors = ButtonDefaults.buttonColors(
            backgroundColor = bgColor,
            contentColor = if (active || !disabled) Color.White else disabledContent,
            disabledBackgroundColor = disabledBackground,
            disabledContentColor = disabledContent
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Text(text = label, style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
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
    onLongPressStop: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Timers", style = MaterialTheme.typography.h6)

            // Audio Timer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAudioTimerToggle,
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (audioTimerPlaying) warningOrange else infoCyan)
                ) {
                    Text(if (audioTimerPlaying) "Stop Audio ($audioTimerLeft s)" else "Start Audio Timer")
                }
            }

            // Countdown Timer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${timeLeft.toString().take(5)}s", style = MaterialTheme.typography.h3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCountdownStart,
                        enabled = !timerRunning,
                        colors = ButtonDefaults.buttonColors(backgroundColor = successGreen)
                    ) { Text("Start") }

                    // Box helps with LongPress for Stop
                    // Note: Simplified for this file - actual long press logic usually needs `combinedClickable`
                    // Using simple Button for now, assuming Model handles secondary logic or dialog trigger
                    Button(
                        onClick = onCountdownStop,
                        enabled = timerRunning || timeLeft > 0, // Allow stop if running or paused?
                        colors = ButtonDefaults.buttonColors(backgroundColor = errorRed)
                    ) { Text("Stop") }
                }
            }
            if (!timerRunning && timeLeft > 0 && timeLeft < 60) {
                TextButton(onClick = onLongPressStop) {
                    Text("Edit Time")
                }
            }
        }
    }
}

// ... Additional helper composables (Queue, ImportExport, ControlRow) would follow standard patterns ...
// Using placeholders for brevity in this fix to ensure compilation

@Composable
fun SevenUpControlRow(screenModel: SevenUpScreenModel, uiState: SevenUpUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { screenModel.undo() }, colors = ButtonDefaults.buttonColors(backgroundColor = warningOrange)) {
            Text("Undo")
        }
        Button(onClick = { screenModel.toggleGridVersion() }) {
            Text("Version ${uiState.gridVersion + 1}")
        }
        Button(onClick = { screenModel.toggleAllRollers() }, colors = ButtonDefaults.buttonColors(backgroundColor = if(uiState.allRollers) successGreen else disabledBackground)) {
            Text("All Rollers")
        }
    }
}

@Composable
fun SevenUpQueueCard(uiState: SevenUpUiState) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            item { Text("Queue (${uiState.queue.size})", fontWeight = FontWeight.Bold) }
            items(uiState.queue) { p ->
                Text("${p.handler} - ${p.dog}", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun SevenUpImportExportCard(onImportClick: () -> Unit, onExportClick: () -> Unit, onLogClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        OutlinedButton(onClick = onImportClick) { Text("Import") }
        OutlinedButton(onClick = onExportClick) { Text("Export") }
        OutlinedButton(onClick = onLogClick) { Text("Log") }
    }
}

// Data Classes for UI State (Assuming ScreenModel is updated to support this)
// These should ideally be in SevenUpScreenModel.kt, but defining minimal structures here if needed for Preview/Compilation check
// NOTE: Actual ScreenModel implementation must provide these.

// SevenUpCell moved to SevenUpScreenModel.kt

fun getSevenUpLayout(version: Int, flipped: Boolean): List<List<SevenUpCell>> {
    // Basic implementation of one version for compilation; expand for all 11 versions
    // Grid: 3 rows, 5 cols.
    // Default Layout (Version 0):
    // Row 0: ., Jump3, ., ., Jump7
    // Row 1: Jump1, ., SweetSpot, Jump5, .
    // Row 2: Jump2, Jump4, ., Jump6, .

    // Simplification: Using Version 0 fixed map for now, logic can be expanded
    val raw = listOf(
        listOf(SevenUpCell.NonJump(""), SevenUpCell.Jump("J3", "3"), SevenUpCell.NonJump(""), SevenUpCell.NonJump(""), SevenUpCell.Jump("J7", "7")),
        listOf(SevenUpCell.Jump("J1", "1"), SevenUpCell.NonJump(""), SevenUpCell.NonJump("SS", true), SevenUpCell.Jump("J5", "5"), SevenUpCell.NonJump("")),
        listOf(SevenUpCell.Jump("J2", "2"), SevenUpCell.Jump("J4", "4"), SevenUpCell.NonJump(""), SevenUpCell.Jump("J6", "6"), SevenUpCell.NonJump(""))
    )

    // Applying Flip if needed (reverse rows and cols)
    if (flipped) {
        return raw.reversed().map { it.reversed() }
    }
    return raw
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

// Stub for Missing ScreenModel/State classes if they don't exist yet
// Assuming SevenUpScreenModel exists and returns SevenUpUiState

