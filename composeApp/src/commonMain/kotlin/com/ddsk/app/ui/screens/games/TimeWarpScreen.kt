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
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import com.ddsk.app.ui.theme.Palette

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
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpotClicked by screenModel.sweetSpotClicked.collectAsState()
        val allRollersClicked by screenModel.allRollersClicked.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val timeRemaining by screenModel.timeRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
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

        val audio = rememberAudioPlayer(getTimerAssetForGame("Time Warp"))

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audio.play() else audio.stop()
        }

        // Add team dialog state
        var handler by remember { mutableStateOf("") }
        var dog by remember { mutableStateOf("") }
        var utn by remember { mutableStateOf("") }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left: Controls & Field
                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScoreHeader(
                            score = score,
                            timer = timeRemaining,
                            isTimerRunning = isTimerRunning,
                            activeParticipant = activeParticipant,
                            onTimerClick = { if (isTimerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            onShowTeams = { showTeams = true },
                            onLongPressTimer = { showTimeInput = true }
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

                    // Right: Actions & Stats
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatsCard(misses = misses, ob = ob, onMiss = { screenModel.incrementMisses() }, onOb = { screenModel.incrementOb() })

                        Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Actions", color = Palette.onSurfaceVariant, fontSize = 12.sp)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { filePicker.launch() }, modifier = Modifier.weight(1f)) {
                                        Text("Import")
                                    }
                                    Button(
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
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Export")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { showAddParticipant = true }, modifier = Modifier.weight(1f)) {
                                        Text("Add Team")
                                    }
                                    Button(onClick = { showHelp = true }, modifier = Modifier.weight(1f)) {
                                        Text("Help")
                                    }
                                }

                                ActionButtons(
                                    sweetSpotActive = sweetSpotClicked,
                                    onSweetSpot = { screenModel.handleSweetSpotClick() },
                                    onFlipField = { screenModel.flipField() },
                                    onReset = { screenModel.reset() },
                                    onAddTeam = { showAddParticipant = true },
                                    onHelp = { showHelp = true }
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { navigator.pop() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Home, "Home", tint = Color.Gray)
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
                        screenModel.setTimeManually(timeText)
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
    score: Int,
    timer: Float,
    isTimerRunning: Boolean,
    activeParticipant: TimeWarpParticipant?,
    onTimerClick: () -> Unit,
    onShowTeams: () -> Unit,
    onLongPressTimer: () -> Unit
) {
    Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable { onShowTeams() }) {
                    Text("Current Team", color = Palette.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = activeParticipant?.displayName ?: "No Team Loaded",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }

                TimerButton(
                    timer = timer,
                    isRunning = isTimerRunning,
                    onClick = onTimerClick,
                    onLongPress = onLongPressTimer
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreBox(label = "Score", value = score.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimerButton(
    timer: Float,
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
            Text(timer.formatTime(), color = Color.White)
        }
    }
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
        modifier = Modifier.fillMaxWidth().height(300.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            rows.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (r == 1) 2f else 1f)
                ) {
                    cols.forEach { c ->
                        val isZone1 = c == 0
                        val isZone2 = c == 1
                        val isZone3 = (c == 2 && r == 1)

                        val zoneNumber = when {
                            isZone1 -> 1
                            isZone2 -> 2
                            isZone3 -> 3
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
                                .background(Palette.surface)
                                .padding(2.dp)
                        ) {
                            if (zoneNumber != null) {
                                Card(
                                    backgroundColor = bg,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onZoneClick(zoneNumber) },
                                    elevation = 2.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Zone $zoneNumber",
                                            color = fg,
                                            fontSize = 16.sp,
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
        Button(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
        Button(onClick = onAllRollers, modifier = Modifier.weight(1f)) { Text(if (allRollersActive) "All Rollers (On)" else "All Rollers") }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next") }
    }
}

@Composable
private fun StatsCard(
    misses: Int,
    ob: Int,
    onMiss: () -> Unit,
    onOb: () -> Unit
) {
    Column {
        Text("Misses: $misses")
        Button(onClick = onMiss) { Text("Miss +") }
        Text("OB: $ob")
        Button(onClick = onOb) { Text("OB +") }
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
