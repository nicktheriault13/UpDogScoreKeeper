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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        var showTeams by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var showTimeInput by remember { mutableStateOf(false) }

        // Import / Export
        val assetLoader = rememberAssetLoader()
        val exporter = rememberFileExporter()
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

        // Add team dialog state
        var handler by remember { mutableStateOf("") }
        var dog by remember { mutableStateOf("") }
        var utn by remember { mutableStateOf("") }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Home button is rendered inside the score header to avoid overlap.

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left column: give it a fixed width and let it scroll/grow vertically.
                    Column(
                        modifier = Modifier.width(760.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScoreHeader(
                            navigator = navigator,
                            score = score,
                            misses = misses,
                            ob = ob,
                            timer = timeRemaining,
                            isTimerRunning = isTimerRunning,
                            isAudioTimerPlaying = isAudioTimerPlaying,
                            onTimerAudioToggle = screenModel::toggleAudioTimer,
                            onStartStopCountdown = {
                                if (isTimerRunning) screenModel.stopCountdownAndAddScore() else screenModel.startCountdown()
                            },
                            canUndo = canUndo,
                            onUndo = { screenModel.undoLastAction() },
                            onMiss = { screenModel.incrementMisses() },
                            onOb = { screenModel.incrementOb() },
                            activeParticipant = activeParticipant,
                            onShowTeams = { showTeams = true },
                            onLongPressStart = { showTimeInput = true }
                        )

                        FieldGrid(
                            clickedZones = clickedZones,
                            fieldFlipped = fieldFlipped,
                            onZoneClick = { screenModel.handleZoneClick(it) }
                        )

                        BottomControls(
                            onPrevious = { /* Optional: not implemented */ },
                            onNext = { screenModel.nextParticipant() },
                            onSkip = { screenModel.skipParticipant() },
                            onShowTeams = { showTeams = true },
                            onAllRollers = { screenModel.toggleAllRollers() },
                            allRollersActive = allRollersClicked
                        )
                    }

                    // Right column: fixed width actions/stats panel
                    Column(
                        modifier = Modifier.width(320.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QueueCard(
                            active = activeParticipant,
                            queue = participantQueue
                        )

                        Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Actions", color = Palette.onSurfaceVariant, fontSize = 12.sp)

                                // 2-column layout
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionButton2Col(text = "Import", onClick = { filePicker.launch() })
                                    ActionButton2Col(
                                        text = "Export",
                                        onClick = {
                                            val template = assetLoader.load("templates/UDC TimeWarp Data Entry L1 or L2 Div Sort.xlsx")
                                            if (template != null) {
                                                val all = buildList {
                                                    activeParticipant?.let { add(it) }
                                                    addAll(participantQueue)
                                                    addAll(completedParticipants)
                                                }
                                                val bytes = generateTimeWarpXlsx(all, template)
                                                exporter.save("TimeWarp_Scores.xlsx", bytes)
                                            }
                                        }
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionButton2Col(text = "Add Team", onClick = { showAddParticipant = true })
                                    ActionButton2Col(text = "Help", onClick = { showHelp = true })
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionButton2Col(
                                        text = if (sweetSpotClicked) "Sweet Spot (On)" else "Sweet Spot",
                                        onClick = { screenModel.handleSweetSpotClick() }
                                    )
                                    ActionButton2Col(text = "Flip Field", onClick = { screenModel.flipField() })
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionButton2Col(text = "Reset", onClick = { screenModel.reset() })
                                    ActionButton2Col(text = if (allRollersClicked) "All Rollers (On)" else "All Rollers", onClick = { screenModel.toggleAllRollers() })
                                }
                            }
                        }
                    }
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
            var timeText by remember { mutableStateOf(timeRemaining.toString()) }
            AlertDialog(
                onDismissRequest = { showTimeInput = false },
                title = { Text("Set Time Remaining") },
                text = {
                    TextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
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

        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                confirmButton = { Button(onClick = { showHelp = false }) { Text("Close") } },
                title = { Text("TimeWarp — Help") },
                text = { Text("- Timer: plays the 60s timer audio.\n- Export: exports participants.\n- Add Team: opens add participant modal.\n- Reset Score: resets scoring and stops timers.\n- Flip Field: toggles field orientation.\n- Zone buttons: mark zones as clicked and update score.\n- Sweet Spot: toggles sweet spot bonus.") }
            )
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
    onZoneClick: (Int) -> Unit
) {
    // 3 rows x 5 cols; middle row taller. Zones: 1 (col0 all rows), 2 (col1 all rows), 3 (col2 middle row)
    val rows = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
    val cols = if (fieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

    Card(
        elevation = 6.dp,
        backgroundColor = Palette.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (r == 1) 2f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cols.forEach { c ->
                        val zoneNumber = when {
                            c == 0 -> 1
                            c == 1 -> 2
                            c == 2 && r == 1 -> 3
                            else -> null
                        }

                        val clicked = zoneNumber != null && clickedZones.contains(zoneNumber)
                        val bg = when {
                            zoneNumber == null -> Palette.surface
                            clicked -> Palette.success
                            else -> Palette.tertiary
                        }
                        val fg = if (clicked) Palette.onSuccess else Palette.onTertiary

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(bg, RoundedCornerShape(12.dp))
                                .clickable(enabled = zoneNumber != null) {
                                    zoneNumber?.let(onZoneClick)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (zoneNumber != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Zone $zoneNumber",
                                        color = fg,
                                        fontWeight = FontWeight.Bold
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
