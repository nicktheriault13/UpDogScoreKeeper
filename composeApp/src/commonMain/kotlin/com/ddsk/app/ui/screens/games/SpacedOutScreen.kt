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
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Removed duplicate expect declaration; rely on shared expect in ReadBinaryFile.common.kt

private fun resolveXlsxInput(rawInput: String): ByteArray? {
    val trimmed = rawInput.trim().trim('"')
    if (trimmed.isEmpty()) return null
    decodeBase64OrNull(trimmed)?.let { return it }
    return readBinaryFile(trimmed)
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64OrNull(raw: String): ByteArray? = runCatching {
    val trimmed = raw.trim().let { content ->
        if (content.contains(",")) content.substringAfterLast(",") else content
    }
    Base64.decode(trimmed.filterNot(Char::isWhitespace))
}.getOrNull()

object SpacedOutScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SpacedOutScreenModel() }
        val score by screenModel.score.collectAsState()
        val spacedOutCount by screenModel.spacedOutCount.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()
        val zonesCaught by screenModel.zonesCaught.collectAsState()
        val clickedZones by screenModel.clickedZonesInRound.collectAsState()
        val sweetSpotBonusOn by screenModel.sweetSpotBonusOn.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val queue by screenModel.participantQueue.collectAsState()
        val logEntries by screenModel.logEntries.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()

        val sidebarCollapsed = rememberSaveable { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }
        var handlerInput by remember { mutableStateOf("") }
        var dogInput by remember { mutableStateOf("") }
        var utnInput by remember { mutableStateOf("") }

        var showImport by remember { mutableStateOf(false) }
        var importBuffer by remember { mutableStateOf("") }
        var importMode by remember { mutableStateOf(ImportMode.CSV) }
        var importError by remember { mutableStateOf<String?>(null) }

        var exportBuffer by remember { mutableStateOf<String?>(null) }
        var logBuffer by remember { mutableStateOf<String?>(null) }

        Surface(color = MaterialTheme.colors.background) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderRow(
                    activeName = activeParticipant?.displayName ?: "No team loaded",
                    score = score,
                    timeLeft = timeLeft,
                    timerRunning = timerRunning,
                    onTimerToggle = {
                        if (timerRunning) screenModel.stopTimer() else screenModel.startTimer()
                    },
                    onTimerReset = screenModel::resetTimer,
                    onResetRound = screenModel::reset
                )

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sidebar(
                        collapsed = sidebarCollapsed.value,
                        score = score,
                        spacedOut = spacedOutCount,
                        misses = misses,
                        ob = ob,
                        sweetSpotBonusOn = sweetSpotBonusOn,
                        onToggleCollapse = { sidebarCollapsed.value = !sidebarCollapsed.value },
                        onMiss = screenModel::incrementMisses,
                        onOb = screenModel::incrementOb,
                        onSweetSpotToggle = screenModel::toggleSweetSpotBonus,
                        onFlipField = screenModel::flipField,
                        onReset = screenModel::reset,
                        onAddTeam = { showAddParticipant = true },
                        onImport = {
                            importMode = ImportMode.CSV
                            importBuffer = ""
                            importError = null
                            showImport = true
                        },
                        onExport = { exportBuffer = screenModel.exportParticipantsAsCsv() },
                        onExportLog = { logBuffer = screenModel.exportLog() },
                        onNext = screenModel::nextParticipant,
                        onPrev = screenModel::previousParticipant,
                        onSkip = screenModel::skipParticipant,
                        onClearParticipants = screenModel::clearParticipants
                    )

                    ParticipantList(queue = queue)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopStats(score, spacedOutCount, zonesCaught, misses, ob)
                        Spacer(modifier = Modifier.height(16.dp))
                        ScoringGrid(fieldFlipped, clickedZones, screenModel)
                        Spacer(modifier = Modifier.height(16.dp))
                        SweetSpotBonusButton(sweetSpotBonusOn, screenModel::toggleSweetSpotBonus)
                    }
                }

                LogCard(logEntries = logEntries)
            }
        }

        LaunchedEffect(sidebarCollapsed.value) {
            scope.launch { scrollState.animateScrollTo(0) }
        }

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
                    screenModel.addParticipant(handlerInput, dogInput, utnInput)
                    handlerInput = ""
                    dogInput = ""
                    utnInput = ""
                    showAddParticipant = false
                }
            )
        }

        if (showImport) {
            ImportDialog(
                mode = importMode,
                text = importBuffer,
                errorMessage = importError,
                onTextChange = {
                    importBuffer = it
                    importError = null
                },
                onModeChange = { mode ->
                    importMode = mode
                    importBuffer = ""
                    importError = null
                },
                onDismiss = {
                    showImport = false
                    importBuffer = ""
                    importError = null
                },
                onConfirm = {
                    when (importMode) {
                        ImportMode.CSV -> {
                            screenModel.importParticipantsFromCsv(importBuffer)
                            showImport = false
                            importBuffer = ""
                        }
                        ImportMode.XLSX -> {
                            val bytes = resolveXlsxInput(importBuffer)
                            if (bytes == null) {
                                importError = "Enter a valid file path or Base64 XLSX payload"
                                return@ImportDialog
                            }
                            screenModel.importParticipantsFromXlsx(bytes)
                            showImport = false
                            importBuffer = ""
                        }
                    }
                }
            )
        }

        exportBuffer?.let { payload ->
            TextPreviewDialog(
                title = "Export Participants",
                text = payload,
                onDismiss = { exportBuffer = null }
            )
        }

        logBuffer?.let { payload ->
            TextPreviewDialog(
                title = "Run Log",
                text = payload,
                onDismiss = { logBuffer = null }
            )
        }
    }
}

@Composable
private fun HeaderRow(
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
            Button(
                onClick = onTimerToggle,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) Color(0xFFFFB74D) else Color(0xFF2196F3))
            ) {
                Text(if (timerRunning) "${timeLeft}s" else "Start Timer", color = Color.White)
            }
            Button(onClick = onTimerReset, enabled = timerRunning.not()) { Text("Reset Timer") }
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
private fun TopStats(score: Int, spacedOut: Int, zones: Int, misses: Int, ob: Int) {
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
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            StatCard(title = "Misses", value = misses.toString(), accent = Color(0xFFD32F2F))
            StatCard(title = "OB", value = ob.toString(), accent = Color(0xFFFFA000))
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
    misses: Int,
    ob: Int,
    sweetSpotBonusOn: Boolean,
    onToggleCollapse: () -> Unit,
    onMiss: () -> Unit,
    onOb: () -> Unit,
    onSweetSpotToggle: () -> Unit,
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
            SidebarButton(label = "Misses: $misses", color = Color(0xFFD32F2F), onClick = onMiss)
            SidebarButton(label = "OB: $ob", color = Color(0xFFFFA000), onClick = onOb)
            SidebarButton(
                label = if (sweetSpotBonusOn) "Sweet Spot Bonus" else "Sweet Spot Bonus",
                color = if (sweetSpotBonusOn) Color(0xFF2E7D32) else MaterialTheme.colors.primary,
                onClick = onSweetSpotToggle
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
private fun SidebarButton(label: String, color: Color = MaterialTheme.colors.primary, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                        Text(participant.displayName, fontWeight = FontWeight.SemiBold)
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
private fun ImportDialog(
    mode: ImportMode,
    text: String,
    errorMessage: String?,
    onTextChange: (String) -> Unit,
    onModeChange: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Participants") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportMode.values().forEach { option ->
                        Button(
                            onClick = { onModeChange(option) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (option == mode) MaterialTheme.colors.primary else MaterialTheme.colors.secondary
                            )
                        ) {
                            Text(option.label)
                        }
                    }
                }
                val labelText = if (mode == ImportMode.CSV) "Paste CSV (handler,dog,utn)" else "Enter XLSX file path or Base64 data"
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    label = { Text(labelText) }
                )
                errorMessage?.let { Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp) }
                if (mode == ImportMode.XLSX) {
                    Text(
                        text = "Tip: you can paste a Base64 data URI or provide an absolute file path to the .xlsx file.",
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colors.onSurface)
            .padding(12.dp)
    ) {
        Text("Scoring Grid", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .border(1.dp, MaterialTheme.colors.onSurface)
        ) {
            val rows = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val cols = if (fieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)
            Column {
                rows.forEach { row ->
                    Row(modifier = Modifier.weight(if (row == 1) 2f else 1f)) {
                        cols.forEach { col ->
                            val zone = getZoneForCell(row, col)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colors.onSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (zone != null) {
                                    ZoneButton(
                                        zone = zone,
                                        clickedZones = clickedZones,
                                        label = screenModel.zoneLabel(zone),
                                        points = screenModel.zonePoints(zone),
                                        onClick = { screenModel.handleZoneClick(zone) }
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
private fun ZoneButton(
    zone: SpacedOutZone,
    clickedZones: Set<SpacedOutZone>,
    label: String,
    points: Int,
    onClick: () -> Unit
) {
    val isClicked = zone in clickedZones
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isClicked) Color(0xFF2E7D32) else MaterialTheme.colors.secondary)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = "+$points", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun SweetSpotBonusButton(isOn: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(backgroundColor = if (isOn) Color(0xFF2E7D32) else MaterialTheme.colors.primary),
        modifier = Modifier.fillMaxWidth(0.5f)
    ) {
        Text(if (isOn) "SweetSpot Bonus Active" else "Activate SweetSpot Bonus")
    }
}

private fun getZoneForCell(row: Int, col: Int): SpacedOutZone? {
    return when {
        row == 2 && col == 1 -> SpacedOutZone.ZONE_1
        row == 0 && col == 2 -> SpacedOutZone.ZONE_2
        row == 0 && col == 3 -> SpacedOutZone.ZONE_3
        row == 1 && col == 2 -> SpacedOutZone.SWEET_SPOT_GRID
        else -> null
    }
}

enum class ImportMode(val label: String) { CSV("CSV"), XLSX("XLSX") }


