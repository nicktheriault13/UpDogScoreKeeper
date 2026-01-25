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
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }
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
        val currentAudioTime by audioPlayer.currentTime.collectAsState()
        val audioRemainingSeconds = remember(audioPlayer.duration, currentAudioTime) {
            ((audioPlayer.duration - currentAudioTime) / 1000).coerceAtLeast(0)
        }

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top row
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: Header card with stats and score
                    HeaderCard(
                        navigator = navigator,
                        score = score,
                        quads = quads,
                        misses = misses,
                        activeParticipant = participants.firstOrNull(),
                        onUndo = screenModel::undo,
                        onMissClick = screenModel::addMiss,
                        onAllRollersClick = screenModel::toggleAllRollers,
                        allRollers = allRollers,
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    )

                    // Right: Timer and controls
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimerCard(
                            timerRunning = timerRunning,
                            timeLeft = audioRemainingSeconds,
                            modifier = Modifier.fillMaxWidth(),
                            onStartStop = { timerRunning = !timerRunning }
                        )

                        ParticipantQueueCard(
                            participants = participants,
                            completedCount = completed.size,
                            onAddTeam = { showAddParticipant = true }
                        )
                    }
                }

                // Middle row - Main game grid
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main grid (center)
                    FourWayGrid(
                        clickedZones = clickedZones,
                        fieldFlipped = fieldFlipped,
                        sweetSpot = sweetSpot,
                        allRollers = allRollers,
                        onZoneClick = { screenModel.handleZoneClick(it) },
                        onSweetSpotClick = screenModel::toggleSweetSpot,
                        modifier = Modifier.weight(1f)
                    )

                    // Right side: Queue and Team Management
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Queue card showing teams in queue
                        QueueCard(
                            participants = participants,
                            modifier = Modifier.weight(1f)
                        )

                        // Team Management section
                        TeamManagementCard(
                            onClearTeams = { showClearTeamsDialog = true },
                            onImport = { filePicker.launch() },
                            onAddTeam = { showAddParticipant = true },
                            onExport = {
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
                                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                        fun pad2(n: Int) = n.toString().padStart(2, '0')
                                        val stamp = buildString {
                                            append(now.year)
                                            append(pad2(now.monthNumber))
                                            append(pad2(now.dayOfMonth))
                                            append('_')
                                            append(pad2(now.hour))
                                            append(pad2(now.minute))
                                            append(pad2(now.second))
                                        }
                                        val fileName = "4WayPlay_Scores_${stamp}.xlsm"
                                        fileExporter.save(fileName, bytes)
                                        exportMessage = "Exported ${exportRows.size} teams"
                                    }
                                    showExportDialog = true
                                }
                            },
                            onLog = { /* LOG */ },
                            onResetRound = { showResetRoundDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bottom row - Navigation buttons aligned below the grid
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
                            onClick = screenModel::flipField,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4), // Purple
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
                            onClick = screenModel::moveToPreviousParticipant,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4), // Purple
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
                            onClick = screenModel::moveToNextParticipant,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4), // Purple
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
                            onClick = screenModel::skipParticipant,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF6750A4), // Purple
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

        if (showClearTeamsDialog) {
            AlertDialog(
                onDismissRequest = { showClearTeamsDialog = false },
                title = { Text("Clear All Teams?") },
                text = { Text("Are you sure you want to clear all teams? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            // Call clear teams function when implemented
                            // screenModel.clearParticipants()
                            showClearTeamsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    Button(onClick = { showClearTeamsDialog = false }) {
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
                    Button(
                        onClick = {
                            screenModel.resetScoring()
                            showResetRoundDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD50000),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    Button(onClick = { showResetRoundDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }
    }
}

@Composable
private fun HeaderCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    score: Int,
    quads: Int,
    misses: Int,
    activeParticipant: FourWayPlayScreenModel.Participant?,
    onUndo: () -> Unit,
    onMissClick: () -> Unit,
    onAllRollersClick: () -> Unit,
    allRollers: Boolean,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Column 1: Title and UNDO button
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GameHomeButton(navigator = navigator)
                        Text(
                            text = "4 Way Play L1",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "# Discs Allowed: 4",
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }

                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFD50000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.width(80.dp).height(32.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Column 2: Main stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Spacer(modifier = Modifier.height(26.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Catches Scored:", fontSize = 11.sp)
                    Text("$score", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Quads Completed:", fontSize = 11.sp)
                    Text("$quads", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }


                // Missed Catches button
                Button(
                    onClick = onMissClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFFF8A50),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MISS", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("$misses", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            // Column 3: Score and All Rollers
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sweet Spot Bonus:", fontSize = 10.sp, color = Color.Gray)

                    // Score on one line
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Score: ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = score.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                // All Rollers button
                Button(
                    onClick = onAllRollersClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (allRollers) Color(0xFF00C853) else Color(0xFF9E9E9E),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
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
            val verticalPadding = 16.dp
            val interCellSpacing = 12.dp

            // Calculate grid dimensions based on requirements:
            // - Top/bottom rows same height
            // - Each column twice as wide as top row height
            // - Center row as tall as it is wide (square)

            val availableWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val availableHeight = (maxHeight - verticalPadding * 2).coerceAtLeast(0.dp)

            // From width: 6h + 2*spacing = availableWidth
            // h = (availableWidth - 2*spacing) / 6
            val topRowHeightFromWidth = ((availableWidth - interCellSpacing * 2) / 6).coerceAtLeast(30.dp)

            // From height: 2h + 2h + 2*spacing = availableHeight (top + center + bottom + spacing)
            // 4h + 2*spacing = availableHeight
            // h = (availableHeight - 2*spacing) / 4
            val topRowHeightFromHeight = ((availableHeight - interCellSpacing * 2) / 4).coerceAtLeast(30.dp)

            // Use the smaller of the two constraints to ensure it fits
            val topRowHeight = minOf(topRowHeightFromWidth, topRowHeightFromHeight).coerceIn(30.dp, 120.dp)
            val columnWidth = topRowHeight * 2
            val centerRowHeight = columnWidth // Square: height = width

            val finalTopRowHeight = topRowHeight
            val finalColumnWidth = columnWidth
            val finalCenterRowHeight = centerRowHeight
            val finalSpacing = interCellSpacing

            val rowOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val colOrder = if (fieldFlipped) listOf(3, 2, 1, 0, -1) else listOf(-1, 0, 1, 2, 3)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.spacedBy(finalSpacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rowOrder.forEach { row ->
                    val rowHeight = when (row) {
                        1 -> finalCenterRowHeight
                        else -> finalTopRowHeight
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(finalSpacing, Alignment.CenterHorizontally)
                    ) {
                        colOrder.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .width(finalColumnWidth)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    // Zone buttons in the 3x3 center grid
                                    row == 0 && col == 0 -> ZoneButton(1, 1 in clickedZones) { onZoneClick(1) }
                                    row == 0 && col == 2 -> ZoneButton(2, 2 in clickedZones) { onZoneClick(2) }
                                    row == 2 && col == 2 -> ZoneButton(3, 3 in clickedZones) { onZoneClick(3) }
                                    row == 2 && col == 0 -> ZoneButton(4, 4 in clickedZones) { onZoneClick(4) }
                                    row == 1 && col == 1 -> SweetSpotButton(sweetSpot, onSweetSpotClick)
                                    // All other positions (including left column -1 and right column 3)
                                    else -> {
                                        // Empty space (light gray background)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                        )
                                    }
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
    // Keep clicked zones GREEN (React behavior). Never use disabled/grey styling.
    val colors = if (clicked) {
        ButtonDefaults.buttonColors(
            backgroundColor = FourWayPalette.success,
            contentColor = FourWayPalette.onSuccess,
            disabledBackgroundColor = FourWayPalette.success,
            disabledContentColor = FourWayPalette.onSuccess
        )
    } else {
        ButtonDefaults.buttonColors(
            backgroundColor = FourWayPalette.primary,
            contentColor = FourWayPalette.onPrimary,
            disabledBackgroundColor = FourWayPalette.primary,
            disabledContentColor = FourWayPalette.onPrimary
        )
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
private fun QueueCard(
    participants: List<FourWayPlayScreenModel.Participant>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

            // Display teams in queue - scrollable, shows 5 entries without scrolling
            // Each entry is ~36dp (8dp padding * 2 + ~20dp text height), so 5 entries = ~180dp
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                        items(participants.size) { index ->
                            val participant = participants[index]
                            val isCurrentTeam = index == 0
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
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
}

@Composable
private fun TeamManagementCard(
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
private fun ControlRow(screenModel: FourWayPlayScreenModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ControlActionButton("Previous", FourWayPalette.warning, { screenModel.moveToPreviousParticipant() })
        ControlActionButton("Skip", FourWayPalette.warning, { screenModel.skipParticipant() })
        ControlActionButton("Next Team", FourWayPalette.primary, { screenModel.moveToNextParticipant() })
        ControlActionButton("Flip Field", FourWayPalette.info, { screenModel.flipField() }, FourWayPalette.onInfo)
        ControlActionButton("Reset Round", FourWayPalette.error, { screenModel.resetScoring() })
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
private fun TimerCard(timerRunning: Boolean, timeLeft: Int, modifier: Modifier = Modifier, onStartStop: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: TIMER and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (!timerRunning) onStartStop() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▶", fontSize = 16.sp)
                        Text("TIMER", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = { if (timerRunning) onStartStop() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏸", fontSize = 16.sp)
                        Text("PAUSE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                    text = if (timerRunning) "${timeLeft}s" else "Ready",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timerRunning) Color(0xFF00BCD4) else Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                if (timerRunning) {
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
private fun ParticipantQueueCard(participants: List<FourWayPlayScreenModel.Participant>, completedCount: Int, onAddTeam: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "On the Field:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            // Show up to 4 teams with their display info
            Column(modifier = Modifier.heightIn(max = 120.dp)) {
                participants.take(4).forEachIndexed { index, participant ->
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "${index + 1}. ${participant.handler} & ${participant.dog}",
                            fontSize = 11.sp
                        )
                        Text(
                            text = "   ${participant.utn}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportExportCard(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onNextTeam: () -> Unit,
    onFlipField: () -> Unit,
    onResetRound: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // We want this card to fit across many device sizes without requiring scrolling.
            // Approximate the vertical space we need and dynamically scale:
            // - header row
            // - 4 rows of buttons in a 2-col grid
            // - internal paddings/spacings
            val availableHeight = maxHeight

            // Defaults (nice on desktop/tablet)
            val basePadding = 16.dp
            val baseGap = 10.dp
            val baseRowGap = 8.dp
            val baseButtonHeight = 40.dp
            val baseFont = 14.sp

            // Compute a compact version if height is constrained (typical Android phones).
            // If maxHeight is unspecified/infinite, stick to defaults.
            val isHeightConstrained = availableHeight != Dp.Unspecified && availableHeight != Dp.Infinity && availableHeight > 0.dp

            // Estimate required height with base values.
            // (Using conservative estimates so we don't overflow.)
            val headerHeight = 28.dp
            val rows = 4
            val estimatedBaseHeight =
                (basePadding * 2) +
                    headerHeight +
                    baseGap +
                    (baseButtonHeight * rows.toFloat()) +
                    (baseRowGap * (rows - 1).toFloat())

            val scale = if (isHeightConstrained) {
                // Keep within [0.78, 1.0] to preserve tap targets while still fitting.
                (availableHeight / estimatedBaseHeight).coerceIn(0.78f, 1.0f)
            } else {
                1.0f
            }

            val padding = basePadding * scale
            val gap = baseGap * scale
            val rowGap = baseRowGap * scale
            val buttonHeight = (baseButtonHeight * scale).coerceAtLeast(34.dp) // keep minimum touch target-ish
            val labelFont = (baseFont.value * scale).coerceIn(11f, 14f).sp

            Column(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Text(text = "Data", style = MaterialTheme.typography.h6)

                // Two-column grid of actions
                Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onPrevious,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.warning, contentColor = Color.White)
                        ) {
                            Text("Previous", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = onSkip,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.warning, contentColor = Color.White)
                        ) {
                            Text("Skip", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onNextTeam,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.primary, contentColor = Color.White)
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = onFlipField,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.info, contentColor = FourWayPalette.onInfo)
                        ) {
                            Text("Flip", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onResetRound,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.error, contentColor = Color.White)
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = onImportClick,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.info, contentColor = Color.White)
                        ) {
                            Text("Import", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onExportClick,
                            modifier = Modifier.weight(1f).height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(backgroundColor = FourWayPalette.info, contentColor = Color.White)
                        ) {
                            Text("Export", fontWeight = FontWeight.Bold, fontSize = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// NOTE: FourWayPlayExportParticipant and generateFourWayPlayXlsx are declared in FileUtils.kt (expect/actual).
