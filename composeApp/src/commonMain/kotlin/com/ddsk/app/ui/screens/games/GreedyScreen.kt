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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.persistence.*
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ddsk.app.ui.theme.Palette

private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val greedyPink = Color(0xFFF500A1)
private val infoBlue = Color(0xFF2196F3)
private val onSurfaceVariant = Color(0xFF49454F)

object GreedyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { GreedyScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val score by screenModel.score.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val throwZone by screenModel.throwZone.collectAsState()
        val rotationDegrees by screenModel.rotationDegrees.collectAsState()
        val sweetSpotBonus by screenModel.sweetSpotBonus.collectAsState()
        val allRollersEnabled by screenModel.allRollersEnabled.collectAsState()

        val activeButtons by screenModel.activeButtons.collectAsState()
        val participants by screenModel.participants.collectAsState()

        val assetLoader = rememberAssetLoader()
        val exporter = rememberFileExporter()
        val scope = rememberCoroutineScope()

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()
        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        var showAddParticipant by remember { mutableStateOf(false) }
        var showImportModeDialog by remember { mutableStateOf(false) }
        // Hold the latest file import payload until the user chooses Add vs Replace.
        var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }
        var showSweetSpotBonusDialog by remember { mutableStateOf(false) }
        var sweetSpotBonusInput by remember { mutableStateOf("") }

        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImportResult = result
                    showImportModeDialog = true
                }
                else -> {}
            }
        }

        // Audio timer: use same as the React page (FunKey-like 75s)
        val timerRunning = remember { mutableStateOf(false) }
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Greedy") })

        LaunchedEffect(timerRunning.value) {
            if (timerRunning.value) audioPlayer.play() else audioPlayer.stop()
        }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Home button is rendered inside the score card to avoid overlap.

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columnSpacing = 16.dp
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(columnSpacing),
                        horizontalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        // Left: scoring + controls
                        Column(
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(columnSpacing)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GreedyScoreSummaryCard(
                                        navigator = navigator,
                                        handlerDog = participants.firstOrNull()?.let { "${it.handler} & ${it.dog}" } ?: "No active team",
                                        throwZone = throwZone,
                                        score = score,
                                        misses = misses,
                                        sweetSpotBonus = sweetSpotBonus,
                                        allRollers = allRollersEnabled,
                                        onUndo = screenModel::undo,
                                        onSweetSpotBonus = { showSweetSpotBonusDialog = true },
                                        sweetSpotBonusEntered = sweetSpotBonus > 0,
                                        onMissPlus = screenModel::incrementMisses,
                                        onAllRollersToggle = screenModel::toggleAllRollers,
                                        allRollersEnabled = allRollersEnabled
                                    )
                                }

                                GreedyZoneControlCard(
                                    throwZone = throwZone,
                                    anySquareClicked = activeButtons.isNotEmpty(),
                                    onNextThrowZoneClockwise = { screenModel.nextThrowZone(clockwise = true) },
                                    onNextThrowZoneCounterClockwise = { screenModel.nextThrowZone(clockwise = false) },
                                    clockwiseDisabled = screenModel.isClockwiseDisabled.collectAsState().value,
                                    counterClockwiseDisabled = screenModel.isCounterClockwiseDisabled.collectAsState().value,
                                    rotateStartingVisible = screenModel.isRotateStartingZoneVisible.collectAsState().value,
                                    onRotateStartingZone = screenModel::rotateStartingZone,
                                    modifier = Modifier.width(190.dp)
                                )
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    // Give the board a chance to grow, but also guarantee a minimum so it doesn't collapse.
                                    .weight(1f, fill = true)
                                    .heightIn(min = 260.dp)
                                    .fillMaxWidth()
                            ) {
                                // Fit the board to the available height so it doesn't get clipped.
                                val boardSide = minOf(maxWidth, maxHeight)
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    GreedyBoard(
                                        rotationDegrees = rotationDegrees,
                                        activeButtons = activeButtons,
                                        throwZone = throwZone,
                                        onPress = screenModel::handleButtonPress,
                                        modifier = Modifier.size(boardSide)
                                    )
                                }
                            }

                            GreedyControlRow(
                                timerRunning = timerRunning.value,
                                onToggleTimer = { timerRunning.value = !timerRunning.value },
                                onReset = screenModel::reset
                            )
                        }

                        // Right: queue + import/export (Boom-style)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(columnSpacing)
                        ) {
                            GreedyImportExportCard(
                                onImportClick = { filePicker.launch() },
                                onExportClick = {
                                    val template = assetLoader.load("templates/UDC Greedy Data Entry L1 Div Sort.xlsx")
                                    if (template != null) {
                                        val bytes = screenModel.exportParticipantsAsXlsx(template)
                                        exporter.save("Greedy_Scores.xlsx", bytes)
                                    }
                                },
                                onExportCsvClick = {
                                    val csv = screenModel.exportParticipantsAsCsv()
                                    scope.launch { saveJsonFileWithPicker("Greedy_Scores.csv", csv) }
                                },
                                onAddTeamClick = { showAddParticipant = true },
                                onClearClick = screenModel::clearParticipants,
                                onPreviousParticipant = screenModel::previousParticipant,
                                onSkipParticipant = screenModel::skipParticipant,
                                onNextParticipant = screenModel::nextParticipant
                            )
                        }
                    }
                }
            }
        }

        if (showAddParticipant) {
            GreedyParticipantDialog(
                onDismiss = { showAddParticipant = false },
                onConfirm = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    showAddParticipant = false
                }
            )
        }

        if (showImportModeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportModeDialog = false
                    pendingImportResult = null
                },
                title = { Text("Import participants") },
                text = { Text("Do you want to add the new participants to the existing list, or delete all existing participants and replace them?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val import = pendingImportResult
                            showImportModeDialog = false
                            pendingImportResult = null
                            if (import != null) {
                                scope.launch {
                                    when (import) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, GreedyScreenModel.ImportMode.Add)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, GreedyScreenModel.ImportMode.Add)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val import = pendingImportResult
                                showImportModeDialog = false
                                pendingImportResult = null
                                if (import != null) {
                                    scope.launch {
                                        when (import) {
                                            is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, GreedyScreenModel.ImportMode.ReplaceAll)
                                            is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, GreedyScreenModel.ImportMode.ReplaceAll)
                                            else -> {}
                                        }
                                    }
                                }
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

        if (showSweetSpotBonusDialog) {
            AlertDialog(
                onDismissRequest = { showSweetSpotBonusDialog = false },
                title = { Text("Sweet Spot Bonus") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a value from 1 to 8.")
                        OutlinedTextField(
                            value = sweetSpotBonusInput,
                            onValueChange = { sweetSpotBonusInput = it.take(1) },
                            label = { Text("Bonus") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val v = sweetSpotBonusInput.toIntOrNull()
                            if (v != null) {
                                screenModel.setSweetSpotBonus(v)
                            }
                            sweetSpotBonusInput = ""
                            showSweetSpotBonusDialog = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        sweetSpotBonusInput = ""
                        showSweetSpotBonusDialog = false
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun GreedyScoreSummaryCard(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    handlerDog: String,
    throwZone: Int,
    score: Int,
    misses: Int,
    sweetSpotBonus: Int,
    allRollers: Boolean,
    onUndo: () -> Unit,
    onSweetSpotBonus: () -> Unit,
    sweetSpotBonusEntered: Boolean,
    onMissPlus: () -> Unit,
    onAllRollersToggle: () -> Unit,
    allRollersEnabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
         Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameHomeButton(navigator = navigator)
                Text(text = "Score: $score", style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Throw Zone: $throwZone",
                    style = MaterialTheme.typography.body2,
                    color = onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Misses: $misses • Bonus: $sweetSpotBonus",
                    style = MaterialTheme.typography.body2,
                    color = onSurfaceVariant
                )
                Text(
                    text = if (allRollers) "All Rollers: Y" else "All Rollers: N",
                    style = MaterialTheme.typography.body2,
                    color = onSurfaceVariant
                )
            }
            Text(
                text = "Active: $handlerDog",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onUndo) { Text("Undo") }

                Button(
                    onClick = onSweetSpotBonus,
                    enabled = !sweetSpotBonusEntered,
                    colors = ButtonDefaults.buttonColors(backgroundColor = warningOrange)
                ) { Text("Sweet Spot Bonus", color = Color.White, fontSize = 12.sp) }

                Button(
                    onClick = onMissPlus,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD50000))
                ) { Text("Miss+", color = Color.White, fontSize = 12.sp) }

                Button(
                    onClick = onAllRollersToggle,
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (allRollersEnabled) successGreen else Color(0xFF2979FF))
                ) { Text("All Rollers", color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun GreedyBoard(
    rotationDegrees: Int,
    activeButtons: Set<String>,
    throwZone: Int,
    onPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(18.dp), elevation = 8.dp, modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Use responsive padding/spacing so we never clip the 3x3 grid.
            val minSide = minOf(maxWidth, maxHeight)
            val outerPadding = (minSide * 0.06f).coerceIn(8.dp, 16.dp)
            val cellGap = (minSide * 0.04f).coerceIn(6.dp, 12.dp)
            val gridSize = (minSide - outerPadding * 2)

             // Visual rotation like the React implementation:
             // - rotate the square
             // - counter-rotate the text so labels remain upright
             Box(
                 modifier = Modifier
                    .padding(outerPadding)
                    .size(gridSize)
                     .graphicsLayer {
                         rotationZ = rotationDegrees.toFloat()
                         transformOrigin = TransformOrigin(0.5f, 0.5f)
                     }
             ) {
                 // Square (3x3)
                 Column(
                    verticalArrangement = Arrangement.spacedBy(cellGap),
                     modifier = Modifier.matchParentSize()
                 ) {
                     for (r in 0..2) {
                         Row(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(cellGap)
                         ) {
                             for (c in 0..2) {
                                 val label = when {
                                     r == 0 && c == 0 -> "X"
                                     r == 0 && c == 1 -> "Y"
                                     r == 0 && c == 2 -> "Z"
                                     r == 1 && c == 1 -> "Sweet Spot"
                                     else -> ""
                                 }
                                 val clicked = label.isNotBlank() && label in activeButtons
                                 val enabled = label.isNotBlank() && !(throwZone == 4 && "Sweet Spot" in activeButtons && label != "Sweet Spot")

                                 Box(
                                     modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     if (label.isNotBlank()) {
                                         Button(
                                             onClick = { onPress(label) },
                                             enabled = enabled,
                                             colors = ButtonDefaults.buttonColors(
                                                 backgroundColor = if (clicked) successGreen else greedyPink,
                                                 contentColor = Color.White
                                             ),
                                             modifier = Modifier.fillMaxSize()
                                         ) {
                                             Text(
                                                 label,
                                                 fontWeight = FontWeight.Bold,
                                                 textAlign = TextAlign.Center,
                                                 modifier = Modifier.graphicsLayer {
                                                     rotationZ = -rotationDegrees.toFloat()
                                                },
                                                // keep labels from wrapping/clipping on small grids
                                                maxLines = 1
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
private fun GreedyZoneControlCard(
    throwZone: Int,
    anySquareClicked: Boolean,
    onNextThrowZoneClockwise: () -> Unit,
    onNextThrowZoneCounterClockwise: () -> Unit,
    clockwiseDisabled: Boolean,
    counterClockwiseDisabled: Boolean,
    rotateStartingVisible: Boolean,
    onRotateStartingZone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Throw Zone", fontWeight = FontWeight.Bold)

            Button(
                onClick = onNextThrowZoneCounterClockwise,
                enabled = throwZone < 4 && anySquareClicked && !counterClockwiseDisabled,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF625B71)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("<-- Next", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center) }

            Button(
                onClick = onNextThrowZoneClockwise,
                enabled = throwZone < 4 && anySquareClicked && !clockwiseDisabled,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF625B71)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next -->", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center) }

            Button(
                onClick = onRotateStartingZone,
                enabled = rotateStartingVisible && throwZone == 1,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF625B71)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Rotate Start", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun GreedyControlRow(
     timerRunning: Boolean,
     onToggleTimer: () -> Unit,
     onReset: () -> Unit,
 ) {
     Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
         Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
             Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                 Button(
                     onClick = onToggleTimer,
                     colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) warningOrange else infoBlue)
                 ) {
                     Text(if (timerRunning) "Stop Timer" else "Timer", color = Color.White)
                 }
                 Button(onClick = onReset, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB3261E))) {
                     Text("Reset", color = Color.White)
                 }
                 Spacer(modifier = Modifier.weight(1f))
             }
         }
     }
 }

@Composable
private fun GreedyQueueCard(queue: List<GreedyParticipant>, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Remaining (${queue.size})", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(queue) { p ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text("${p.handler} & ${p.dog}", fontWeight = FontWeight.SemiBold)
                        if (p.utn.isNotBlank()) Text(p.utn, fontSize = 12.sp, color = Color.Gray)
                        if (p.heightDivision.isNotBlank()) Text("Height: ${p.heightDivision}", fontSize = 12.sp, color = onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreedyImportExportCard(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onAddTeamClick: () -> Unit,
    onClearClick: () -> Unit,
    onPreviousParticipant: () -> Unit,
    onSkipParticipant: () -> Unit,
    onNextParticipant: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Actions", fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) { Text("Import") }
                Button(onClick = onExportClick, modifier = Modifier.fillMaxWidth()) { Text("Export") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportCsvClick, modifier = Modifier.fillMaxWidth()) { Text("CSV") }
                Button(onClick = onAddTeamClick, modifier = Modifier.fillMaxWidth()) { Text("Add") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPreviousParticipant, modifier = Modifier.fillMaxWidth()) { Text("Previous") }
                Button(onClick = onSkipParticipant, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                Button(onClick = onNextParticipant, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }
            Button(
                onClick = onClearClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB3261E))
            ) {
                Text("Clear", color = Color.White)
            }
        }
    }
}

@Composable
private fun GreedyParticipantDialog(
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
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
