package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        val logEntries by myModel.logEntries.collectAsState()
        val timeLeft by myModel.timeLeft.collectAsState()
        val timerRunning by myModel.timerRunning.collectAsState()
        val pendingJsonExport by myModel.pendingJsonExport.collectAsState()

        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            myModel.consumePendingJsonExport()
        }

        val sidebarCollapsed = rememberSaveable { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }
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

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderRow(
                    onHome = { navigator.replaceAll(com.ddsk.app.ui.screens.MainScreen()) },
                    activeName = activeParticipant?.displayName() ?: "No team loaded",
                    score = score,
                    timeLeft = timeLeft,
                    timerRunning = timerRunning,
                    onTimerToggle = { if (timerRunning) myModel.stopTimer() else myModel.startTimer() },
                    onTimerReset = myModel::resetTimer,
                    onResetRound = myModel::reset
                )

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sidebar(
                        collapsed = sidebarCollapsed.value,
                        score = score,
                        spacedOut = spacedOutCount,
                        sweetSpotBonusOn = sweetSpotBonusOn,
                        allRollersOn = allRollersOn,
                        onToggleCollapse = { sidebarCollapsed.value = !sidebarCollapsed.value },
                        onSweetSpotToggle = myModel::toggleSweetSpotBonus,
                        onAllRollersToggle = myModel::toggleAllRollers,
                        onFlipField = myModel::flipField,
                        onReset = myModel::reset,
                        onAddTeam = { showAddParticipant = true },
                        onImport = { filePicker.launch() },
                        onExport = {
                            val template = assetLoader.load("templates/UDC Spaced Out Data Entry L1 Div Sort.xlsx")
                            if (template != null) {
                                val bytes = myModel.exportParticipantsAsXlsx(template)
                                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                fun pad2(v: Int) = v.toString().padStart(2, '0')
                                val dateStr = buildString {
                                    append(now.year); append(pad2(now.monthNumber)); append(pad2(now.dayOfMonth)); append("_"); append(pad2(now.hour)); append(pad2(now.minute)); append(pad2(now.second))
                                }
                                exporter.save("SpacedOut_Scores_${dateStr}.xlsx", bytes)
                            } else {
                                exportBuffer = myModel.exportParticipantsAsCsv()
                            }
                        },
                        onExportLog = { logBuffer = myModel.exportLog() },
                        onNext = myModel::nextParticipant,
                        onPrev = myModel::previousParticipant,
                        onSkip = myModel::skipParticipant,
                        onClearParticipants = myModel::clearParticipants
                    )

                    ParticipantList(queue = queue)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopStats(
                            score,
                            spacedOutCount,
                            zonesCaught,
                            misses,
                            ob,
                            onMiss = myModel::incrementMisses,
                            onOb = myModel::incrementOb,
                            onNext = myModel::nextParticipant,
                            onSkip = myModel::skipParticipant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ScoringGrid(fieldFlipped, clickedZones, myModel)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SweetSpotBonusButton(modifier = Modifier.weight(1f), bonusOn = sweetSpotBonusOn, onToggle = myModel::toggleSweetSpotBonus)
                            AllRollersButton(enabled = allRollersOn, onToggle = myModel::toggleAllRollers, modifier = Modifier.weight(1f))
                        }
                    }
                }

                LogCard(logEntries = logEntries)

                LaunchedEffect(sidebarCollapsed.value) { scope.launch { scrollState.animateScrollTo(0) } }

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

                exportBuffer?.let { payload -> TextPreviewDialog(title = "Export Participants", text = payload, onDismiss = { exportBuffer = null }) }
                logBuffer?.let { payload -> TextPreviewDialog(title = "Run Log", text = payload, onDismiss = { logBuffer = null }) }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onHome: () -> Unit,
    activeName: String,
    score: Int,
    timeLeft: Int,
    timerRunning: Boolean,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit,
    onResetRound: () -> Unit
) {
    Surface(elevation = 4.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onHome) { Icon(imageVector = Icons.Filled.Menu, contentDescription = "Home") }
            Button(onClick = onTimerToggle, colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) Color(0xFFFFB74D) else Color(0xFF2196F3))) {
                Text(if (timerRunning) "${timeLeft}s" else "Start Timer", color = Color.White)
            }
            Button(onClick = onTimerReset, enabled = !timerRunning) { Text("Reset Timer") }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = activeName, fontWeight = FontWeight.Bold)
                Text(text = "Score: $score", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            }
            Button(onClick = onResetRound, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))) {
                Text("Reset Round", color = Color.White)
            }
        }
    }
}

@Composable
private fun TopStats(
    score: Int,
    spacedOut: Int,
    zones: Int,
    misses: Int,
    ob: Int,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Spaced Out", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            StatCard(title = "Score", value = score.toString())
            StatCard(title = "Spaced Out", value = spacedOut.toString())
            StatCard(title = "Zones", value = zones.toString())
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Miss/OB buttons (replacing the old displays)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SidebarButton(
                label = "Misses: $misses",
                color = Color(0xFFD32F2F),
                onClick = onMiss,
                modifier = Modifier.weight(1f)
            )
            SidebarButton(
                label = "OB: $ob",
                color = Color(0xFFFFA000),
                onClick = onOb,
                modifier = Modifier.weight(1f)
            )
        }

        // Participant navigation row (missing in Compose; mirrors React sidebar actions)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SidebarButton(
                label = "Next",
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
            SidebarButton(
                label = "Skip",
                color = Color.LightGray,
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, accent: Color = MaterialTheme.colors.primary) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.surface, shape = MaterialTheme.shapes.medium)
            .padding(12.dp)
            .width(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.body2, color = accent)
        Text(value, style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Sidebar(
    collapsed: Boolean,
    score: Int,
    spacedOut: Int,
    sweetSpotBonusOn: Boolean,
    allRollersOn: Boolean,
    onToggleCollapse: () -> Unit,
    onSweetSpotToggle: () -> Unit,
    onAllRollersToggle: () -> Unit,
    onFlipField: () -> Unit,
    onReset: () -> Unit,
    onAddTeam: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportLog: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onClearParticipants: () -> Unit
) {
    val width = if (collapsed) 56.dp else 220.dp
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colors.surface, shape = MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        IconButton(onClick = onToggleCollapse, modifier = Modifier.align(Alignment.End)) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Toggle Sidebar")
        }

        if (!collapsed) {
            Text("Controls", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SidebarButton(
                label = if (sweetSpotBonusOn) "Sweet Spot Bonus" else "Sweet Spot Bonus",
                color = if (sweetSpotBonusOn) Color(0xFF2E7D32) else MaterialTheme.colors.primary,
                onClick = onSweetSpotToggle
            )
            SidebarButton(
                label = if (allRollersOn) "All Rollers" else "All Rollers",
                color = if (allRollersOn) Color(0xFF2E7D32) else MaterialTheme.colors.primary,
                onClick = onAllRollersToggle
            )
            SidebarButton(label = "Flip Field", onClick = onFlipField)
            SidebarButton(label = "Reset Round", onClick = onReset)

            Spacer(modifier = Modifier.height(12.dp))
            Text("Participants", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SidebarButton(label = "Add Team", onClick = onAddTeam)
            SidebarButton(label = "Import", onClick = onImport)
            SidebarButton(label = "Export", onClick = onExport)
            SidebarButton(label = "Export Log", onClick = onExportLog)
            SidebarButton(label = "Previous", onClick = onPrev)
            SidebarButton(label = "Next", onClick = onNext)
            SidebarButton(label = "Skip", onClick = onSkip)
            SidebarButton(label = "Clear Teams", color = Color(0xFFD50000), onClick = onClearParticipants)

            Spacer(modifier = Modifier.height(12.dp))
            Text("Status", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Score: $score", style = MaterialTheme.typography.body1)
            Text("Spaced Out: $spacedOut", style = MaterialTheme.typography.body1)
        }
    }
}

@Composable
private fun SidebarButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = Color.White)
    }
}

@Composable
private fun ParticipantList(queue: List<SpacedOutParticipant>, modifier: Modifier = Modifier) {
    if (queue.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.width(200.dp).fillMaxHeight(),
            elevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No teams queued", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            }
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.width(200.dp).fillMaxHeight(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Remaining (${queue.size})", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(queue) { participant ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(participant.displayName(), fontWeight = FontWeight.SemiBold)
                        if (participant.utn.isNotBlank()) {
                            Text(participant.utn, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(logEntries: List<String>) {
    Surface(elevation = 4.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Log", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val visibleEntries = logEntries.take(6)
            if (visibleEntries.isEmpty()) {
                Text("No log entries yet", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            } else {
                visibleEntries.forEach { entry ->
                    Text(entry, fontSize = 12.sp)
                }
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
    screenModel: SpacedOutScreenModel
) {
    // React parity:
    // Grid: 3 rows x 5 columns. Middle row is double height.
    // Only 4 scoring buttons are shown at fixed coordinates (after applying flip mapping):
    // - Zone1 at (row=2,col=1)
    // - Sweet Spot (zone button) at (row=1,col=2)
    // - Zone2 at (row=0,col=2)
    // - Zone3 at (row=0,col=3)
    // All other cells are empty.

    val outerShape = RoundedCornerShape(18.dp)
    val cellBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
    val zoneShape = RoundedCornerShape(10.dp)

    // Field sizing: use a fixed height similar to the previous UI, but keep it responsive in width.
    // (React uses ~0.45 * min(windowW, windowH)). On desktop and android this gives stable visuals.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        val fieldWidth = maxWidth
        val fieldHeight = (fieldWidth * 0.5f).coerceAtMost(320.dp).coerceAtLeast(200.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                elevation = 2.dp,
                shape = outerShape,
                color = MaterialTheme.colors.surface,
                modifier = Modifier
                    .width(fieldWidth)
                    .height(fieldHeight)
                    .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.35f), outerShape)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    for (visualRow in 0..2) {
                        Row(modifier = Modifier.weight(if (visualRow == 1) 2f else 1f)) {
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

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(1.dp, cellBorderColor)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (zone != null) {
                                        val isClicked = clickedZones.contains(zone)
                                        val bg = if (isClicked) Color(0xFF00C853) else MaterialTheme.colors.primary
                                        val fg = if (isClicked) Color.White else MaterialTheme.colors.onPrimary

                                        val label = when (zone) {
                                            SpacedOutZone.Zone1 -> "zone1"
                                            SpacedOutZone.Zone2 -> "Sweet Spot"
                                            SpacedOutZone.Zone3 -> "zone2"
                                            SpacedOutZone.Zone4 -> "zone3"
                                            else -> zone.label
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(bg, zoneShape)
                                                .clickable { screenModel.handleZoneClick(zone) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = fg,
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
        }
    }
}

@Composable
private fun SweetSpotBonusButton(modifier: Modifier = Modifier, bonusOn: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (bonusOn) Color(0xFF2E7D32) else Color.LightGray
        ),
        modifier = modifier.height(50.dp)
    ) {
        Text("Sweet Spot Bonus (${if (bonusOn) "ON" else "OFF"})", color = Color.White)
    }
}

@Composable
private fun AllRollersButton(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (enabled) Color(0xFF2E7D32) else Color.LightGray
        ),
        modifier = modifier.height(50.dp)
    ) {
        Text("All Rollers (${if (enabled) "ON" else "OFF"})", color = Color.White)
    }
}
