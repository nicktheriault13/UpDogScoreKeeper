package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.games.ui.GameHomeOverlay
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

// Palette from original file
private object FourWayPalette {
    val primary = Color(0xFF6750A4)
    val onPrimary = Color.White
    val success = Color(0xFF00C853)
    val onSuccess = Color.White
    val warning = Color(0xFFFFB74D) // Lighter orange
    val info = Color(0xFF2979FF)
    val onInfo = Color.White
    val error = Color(0xFFD50000)
    val surfaceContainer = Color(0xFFF5EFF7)
}

object FourWayPlayScreen : Screen {
    // ...

    // Unused colors removed

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FourWayPlayScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val score by screenModel.score.collectAsState()
        val quads by screenModel.quads.collectAsState()
        val misses by screenModel.misses.collectAsState()

        var timerRunning by remember { mutableStateOf(false) }

        val participants by screenModel.participants.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpot by screenModel.sweetSpotClicked.collectAsState()
        val allRollers by screenModel.allRollers.collectAsState()
        val completed by screenModel.completed.collectAsState()

        val assetLoader = rememberAssetLoader()
        val fileExporter = rememberFileExporter()
        val scope = rememberCoroutineScope()

        var showAddParticipant by remember { mutableStateOf(false) }
        var handlerInput by remember { mutableStateOf("") }
        var dogInput by remember { mutableStateOf("") }
        var utnInput by remember { mutableStateOf("") }
        var showExportDialog by remember { mutableStateOf(false) }
        var exportMessage by remember { mutableStateOf("") }

        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("4-Way Play") })

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

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
                    // Left Column (Game Area)
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        ScoreSummaryCard(
                            score = score,
                            quads = quads,
                            misses = misses,
                            activeParticipant = participants.firstOrNull()
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            // Using a placeholder or actual grid component if available
                            // Ensure FourWayPlayGrid or similar is defined
                            // MainGrid(
                            // ...
                            // )
                            // Replacing with FourWayGrid which should be defined below
                            FourWayGrid(
                                clickedZones = clickedZones,
                                fieldFlipped = fieldFlipped,
                                sweetSpot = sweetSpot,
                                allRollers = allRollers,
                                onZoneClick = { screenModel.handleZoneClick(it) },
                                onSweetSpotClick = screenModel::toggleSweetSpot
                            )
                        }
                        ControlRow(
                            screenModel = screenModel,
                            onAddMiss = screenModel::addMiss
                        )
                    }

                    // Right Column (Info & Actions)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                         TimerCard(
                             timerRunning = timerRunning,
                             modifier = Modifier.fillMaxWidth(),
                             onStartStop = { timerRunning = !timerRunning }
                         )

                         ParticipantQueueCard(
                             participants = participants,
                             completedCount = completed.size,
                             onAddTeam = { showAddParticipant = true }
                         )

                         ImportExportCard(
                            onImportClick = { filePicker.launch() },
                            onExportClick = {
                                scope.launch {
                                    val scored = screenModel.getParticipantsForExport()
                                    if (scored.isEmpty()) {
                                        exportMessage = "No scored teams to export"
                                        showExportDialog = true
                                        return@launch
                                    }
                                    val template = assetLoader.load("templates/UDC FourWayPlay Data Entry L1 or L2 Div Sort 6-2023.xlsm")
                                    if (template == null) {
                                        exportMessage = "Template missing"
                                        showExportDialog = true
                                        return@launch
                                    }
                                    val exportRows = scored.map {
                                        FourWayPlayExportParticipant(
                                            handler = it.handler,
                                            dog = it.dog,
                                            utn = it.utn,
                                            zone1Catches = it.zone1Catches,
                                            zone2Catches = it.zone2Catches,
                                            zone3Catches = it.zone3Catches,
                                            zone4Catches = it.zone4Catches,
                                            sweetSpot = if (it.sweetSpot) 1 else 0,
                                            allRollers = if (it.allRollers) 1 else 0,
                                            heightDivision = it.heightDivision,
                                            misses = it.misses
                                        )
                                    }
                                    val bytes = generateFourWayPlayXlsx(exportRows, template)
                                    if (bytes.isEmpty()) {
                                        exportMessage = "Export failed"
                                    } else {
                                        // Use a multiplatform-safe timestamp (doesn't rely on java.lang.System)
                                        val timestamp = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                                        val fileName = "FourWayPlay_${timestamp}.xlsm"
                                        fileExporter.save(fileName, bytes)
                                        exportMessage = "Exported ${exportRows.size} teams"
                                    }
                                    showExportDialog = true
                                }
                            }
                         )
                    }
                }
            }
        }

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export") },
                text = { Text(exportMessage) },
                confirmButton = { Button(onClick = { showExportDialog = false }) { Text("OK") } }
            )
        }

        if (showAddParticipant) {
            AlertDialog(
                onDismissRequest = { showAddParticipant = false },
                title = { Text("Add Team") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = handlerInput, onValueChange = { handlerInput = it }, label = { Text("Handler") })
                        OutlinedTextField(value = dogInput, onValueChange = { dogInput = it }, label = { Text("Dog") })
                        OutlinedTextField(value = utnInput, onValueChange = { utnInput = it }, label = { Text("UTN") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        screenModel.addParticipant(handlerInput, dogInput, utnInput)
                        handlerInput = ""
                        dogInput = ""
                        utnInput = ""
                        showAddParticipant = false
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddParticipant = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ScoreSummaryCard(
    score: Int,
    quads: Int,
    misses: Int,
    activeParticipant: FourWayPlayScreenModel.Participant?
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Score: $score", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            Text(
                text = "Quads: $quads • Misses: $misses",
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            val active = activeParticipant
            Text(
                text = active?.let { "Active: ${it.handler} & ${it.dog} (${it.utn})" } ?: "No active team",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FourWayGrid(
    fieldFlipped: Boolean,
    clickedZones: Set<Int>,
    sweetSpot: Boolean,
    allRollers: Boolean,
    onZoneClick: (Int) -> Unit,
    onSweetSpotClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = 16.dp
            val interCellSpacing = 12.dp
            // Calculations similar to BoomGrid for consistency
            val gridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val widthLimitedCell = ((gridWidth - interCellSpacing * 2) / 3).coerceAtLeast(60.dp) // 3 columns

            val hasFiniteHeight = maxHeight != Dp.Unspecified && maxHeight != Dp.Infinity && maxHeight > 0.dp
            val heightLimitedCell = if (hasFiniteHeight) {
                val totalSpacing = interCellSpacing * 2
                (((maxHeight - totalSpacing) / 3).coerceAtLeast(60.dp)) // 3 rows mostly
            } else {
                widthLimitedCell
            }
            val cellSize = minOf(widthLimitedCell, heightLimitedCell)

            val rowOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val colOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(interCellSpacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rowOrder.forEach { row ->
                    val rowHeight = if (row == 1) cellSize * 1.2f else cellSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(interCellSpacing, Alignment.CenterHorizontally)
                    ) {
                        colOrder.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .width(cellSize)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    row == 0 && col == 0 -> ZoneButton(1, 1 in clickedZones) { onZoneClick(1) }
                                    row == 0 && col == 2 -> ZoneButton(2, 2 in clickedZones) { onZoneClick(2) }
                                    row == 2 && col == 2 -> ZoneButton(3, 3 in clickedZones) { onZoneClick(3) }
                                    row == 2 && col == 0 -> ZoneButton(4, 4 in clickedZones) { onZoneClick(4) }
                                    row == 1 && col == 1 -> SweetSpotButton(sweetSpot, onSweetSpotClick)
                                }
                            }
                        }
                    }
                }
            }

            if (allRollers) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                     Text(
                        text = "All Rollers Active",
                        color = FourWayPalette.success,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.background(FourWayPalette.surfaceContainer, RoundedCornerShape(4.dp)).padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneButton(value: Int, clicked: Boolean, onClick: () -> Unit) {
    val colors = if (clicked) {
        ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.success, contentColor = FourWayPalette.onSuccess)
    } else {
        ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.primary, contentColor = FourWayPalette.onPrimary)
    }
    Button(
        onClick = onClick,
        enabled = !clicked,
        colors = colors,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(value.toString(), fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SweetSpotButton(active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (active) FourWayPalette.success else FourWayPalette.info,
            contentColor = if (active) FourWayPalette.onSuccess else FourWayPalette.onInfo
        ),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (active) "Sweet Spot\nON" else "Sweet Spot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun ControlRow(screenModel: FourWayPlayScreenModel, onAddMiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ControlActionButton("Miss", FourWayPalette.error, onAddMiss)
        ControlActionButton("Undo", FourWayPalette.warning, { screenModel.undo() })
        ControlActionButton("Next Team", FourWayPalette.primary, { screenModel.moveToNextParticipant() })
        ControlActionButton("Flip Field", FourWayPalette.info, { screenModel.flipField() }, FourWayPalette.onInfo)
        ControlActionButton("Reset Round", FourWayPalette.error, { screenModel.resetScoring() }) // Moved to end, dangerous action
    }
}

@Composable
private fun RowScope.ControlActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(56.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = contentColor)
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TimerCard(timerRunning: Boolean, modifier: Modifier = Modifier, onStartStop: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Timer", style = MaterialTheme.typography.h6)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (timerRunning) FourWayPalette.warning else FourWayPalette.success,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (timerRunning) "Stop" else "Start 60s")
                }
            }
        }
    }
}

@Composable
private fun ParticipantQueueCard(participants: List<FourWayPlayScreenModel.Participant>, completedCount: Int, onAddTeam: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Teams", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Remaining: ${participants.size}", style = MaterialTheme.typography.caption)
            Text(text = "Completed: $completedCount", style = MaterialTheme.typography.caption)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAddTeam, modifier = Modifier.fillMaxWidth()) {
                Text("Add Team")
            }
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(participants) { participant ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = "${participant.handler} & ${participant.dog}", fontWeight = FontWeight.Bold)
                        Text(text = participant.utn, style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportExportCard(onImportClick: () -> Unit, onExportClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Data", style = MaterialTheme.typography.h6)
            Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.info, contentColor = Color.White)) {
                Text("Import")
            }
            Button(onClick = onExportClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.info, contentColor = Color.White)) {
                Text("Export Excel")
            }
        }
    }
}

// NOTE: FourWayPlayExportParticipant and generateFourWayPlayXlsx are declared in FileUtils.kt (expect/actual).
