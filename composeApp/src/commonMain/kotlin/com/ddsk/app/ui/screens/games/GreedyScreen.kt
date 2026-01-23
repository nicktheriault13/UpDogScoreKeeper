package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val greedyPink = Color(0xFFF500A1)
private val infoBlue = Color(0xFF2196F3)

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
        val sweetSpotBonus by screenModel.sweetSpotBonus.collectAsState()
        val allRollersEnabled by screenModel.allRollersEnabled.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val rotationDegrees by screenModel.rotationDegrees.collectAsState()
        val isClockwiseDisabled by screenModel.isClockwiseDisabled.collectAsState()
        val isCounterClockwiseDisabled by screenModel.isCounterClockwiseDisabled.collectAsState()

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
        var showClearTeamsDialog by remember { mutableStateOf(false) }
        var showResetRoundDialog by remember { mutableStateOf(false) }

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

        Surface(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBFE))) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top row: Header card and Timer/Control
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Header card with stats and score
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
                            allRollersEnabled = allRollersEnabled,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )

                        // Right: Timer/Control
                        GreedyControlCard(
                            timerRunning = timerRunning.value,
                            onToggleTimer = { timerRunning.value = !timerRunning.value },
                            onReset = screenModel::reset,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }

                    // Middle row: Main game grid and Team Management
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main grid (center)
                        GreedyScoringCard(
                            activeButtons = activeButtons,
                            throwZone = throwZone,
                            onPress = screenModel::handleButtonPress,
                            fieldFlipped = fieldFlipped,
                            rotationDegrees = rotationDegrees,
                            anyButtonClicked = activeButtons.isNotEmpty(),
                            onNextThrowZoneCounterClockwise = { screenModel.nextThrowZone(clockwise = false) },
                            onNextThrowZoneClockwise = { screenModel.nextThrowZone(clockwise = true) },
                            isClockwiseDisabled = isClockwiseDisabled,
                            isCounterClockwiseDisabled = isCounterClockwiseDisabled,
                            modifier = Modifier.weight(1f)
                        )

                        // Right side: Queue and Team Management
                        Column(
                            modifier = Modifier.weight(0.3f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Queue card showing teams in queue
                            GreedyQueueCard(
                                queue = participants,
                                modifier = Modifier.weight(1f)
                            )

                            // Team Management section
                            GreedyTeamManagementCard(
                                onClearTeams = { showClearTeamsDialog = true },
                                onImport = { filePicker.launch() },
                                onAddTeam = { showAddParticipant = true },
                                onExport = {
                                    val template = assetLoader.load("templates/UDC Greedy Data Entry L1 Div Sort.xlsx")
                                    if (template != null) {
                                        val bytes = screenModel.exportParticipantsAsXlsx(template)
                                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                        val stamp = greedyTimestamp(now)
                                        exporter.save("Greedy_Scores_$stamp.xlsx", bytes)
                                    }
                                },
                                onLog = {
                                    val content = screenModel.exportLog()
                                    scope.launch {
                                        saveJsonFileWithPicker("Greedy_Log.txt", content)
                                    }
                                },
                                onResetRound = { showResetRoundDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Bottom row: Navigation buttons aligned below the grid
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
                                    backgroundColor = Color(0xFF6750A4),
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
                                onClick = screenModel::previousParticipant,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
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
                                onClick = screenModel::nextParticipant,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF6750A4),
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
                                    backgroundColor = Color(0xFF6750A4),
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

        if (showClearTeamsDialog) {
            AlertDialog(
                onDismissRequest = { showClearTeamsDialog = false },
                title = { Text("Clear All Teams?") },
                text = { Text("Are you sure you want to clear all teams? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.clearParticipants()
                            showClearTeamsDialog = false
                        }
                    ) {
                        Text("Clear", color = Color(0xFFD50000))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearTeamsDialog = false }) {
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
                    TextButton(
                        onClick = {
                            screenModel.reset()
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
    allRollersEnabled: Boolean,
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
                            text = "Greedy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = handlerDog,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFD50000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.width(100.dp).height(42.dp)
                ) {
                    Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Column 2: Main stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Throw Zone:", fontSize = 14.sp)
                    Text("$throwZone", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Bonus:", fontSize = 14.sp)
                    Text("$sweetSpotBonus", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // MISS button (full width)
                Button(
                    onClick = onMissPlus,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFD50000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MISS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("$misses", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Column 3: Score and special buttons
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // Score on one line
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Score: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = score.toString(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sweet Spot Bonus button
                    Button(
                        onClick = onSweetSpotBonus,
                        enabled = !sweetSpotBonusEntered,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (sweetSpotBonusEntered) Color(0xFF9E9E9E) else warningOrange,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SWEET SPOT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // All Rollers button
                    Button(
                        onClick = onAllRollersToggle,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (allRollersEnabled) successGreen else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ALL ROLLERS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreedyScoringCard(
    activeButtons: Set<String>,
    throwZone: Int,
    onPress: (String) -> Unit,
    fieldFlipped: Boolean,
    rotationDegrees: Int,
    anyButtonClicked: Boolean,
    onNextThrowZoneCounterClockwise: () -> Unit,
    onNextThrowZoneClockwise: () -> Unit,
    isClockwiseDisabled: Boolean,
    isCounterClockwiseDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(18.dp), elevation = 8.dp, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Calculate button positions based on rotation
            // Rotation is clockwise around the Sweet Spot (middle row, col3)
            // 0°: X=top-col2, Y=top-col3, Z=top-col4
            // 90°: X=top-col4, Y=mid-col4, Z=bot-col4
            // 180°: X=bot-col4, Y=bot-col3, Z=bot-col2
            // 270°: X=bot-col2, Y=mid-col2, Z=top-col2
            val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
            val rotationSteps = (normalizedRotation / 90) % 4

            // Define button positions: (row, column, buttonLabel)
            // row: 0=top, 1=middle, 2=bottom; column: 1-5
            data class ButtonPosition(val row: Int, val col: Int, val label: String)

            val buttonPositions = when (rotationSteps) {
                0 -> listOf(
                    ButtonPosition(0, 2, "X"),
                    ButtonPosition(0, 3, "Y"),
                    ButtonPosition(0, 4, "Z")
                )
                1 -> listOf(
                    ButtonPosition(0, 4, "X"),
                    ButtonPosition(1, 4, "Y"),
                    ButtonPosition(2, 4, "Z")
                )
                2 -> listOf(
                    ButtonPosition(2, 4, "X"),
                    ButtonPosition(2, 3, "Y"),
                    ButtonPosition(2, 2, "Z")
                )
                3 -> listOf(
                    ButtonPosition(2, 2, "X"),
                    ButtonPosition(1, 2, "Y"),
                    ButtonPosition(0, 2, "Z")
                )
                else -> listOf(
                    ButtonPosition(0, 2, "X"),
                    ButtonPosition(0, 3, "Y"),
                    ButtonPosition(0, 4, "Z")
                )
            }

            // When flipped, reverse the row order for the display (top becomes bottom)
            // but we need to maintain the actual row indices for button positioning
            val displayRowOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)

            displayRowOrder.forEach { displayRowIndex ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(if (displayRowIndex == 1) 1.5f else 1f)
                ) {
                    for (colIndex in 1..5) {
                        when {
                            // <-Next button ALWAYS in ACTUAL middle row (row 1), column 1 - unaffected by flip
                            displayRowIndex == 1 && colIndex == 1 -> {
                                Button(
                                    onClick = onNextThrowZoneCounterClockwise,
                                    enabled = throwZone < 4 && anyButtonClicked && !isCounterClockwiseDisabled,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (throwZone < 4 && anyButtonClicked && !isCounterClockwiseDisabled) Color(0xFF6750A4) else Color(0xFF9E9E9E),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("<-Next", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            // Sweet Spot ALWAYS in ACTUAL middle row (row 1), column 3 - unaffected by flip
                            displayRowIndex == 1 && colIndex == 3 -> {
                                val clickedSweetSpot = "Sweet Spot" in activeButtons
                                val enabledSweetSpot = !(throwZone == 4 && "Sweet Spot" in activeButtons)
                                Button(
                                    onClick = { onPress("Sweet Spot") },
                                    enabled = enabledSweetSpot,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (clickedSweetSpot) successGreen else greedyPink,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    Text("Sweet Spot", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                            // Next-> button ALWAYS in ACTUAL middle row (row 1), column 5 - unaffected by flip
                            displayRowIndex == 1 && colIndex == 5 -> {
                                Button(
                                    onClick = onNextThrowZoneClockwise,
                                    enabled = throwZone < 4 && anyButtonClicked && !isClockwiseDisabled,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (throwZone < 4 && anyButtonClicked && !isClockwiseDisabled) Color(0xFF6750A4) else Color(0xFF9E9E9E),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Next->", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            // Regular scoring buttons (X, Y, Z) - these ARE affected by flip
                            else -> {
                                // Map the display row back to the actual button position row for lookup
                                val actualRowForButtonLookup = if (fieldFlipped) {
                                    // When flipped, invert the row index for button positions
                                    2 - displayRowIndex
                                } else {
                                    displayRowIndex
                                }

                                val buttonAtPos = buttonPositions.find { it.row == actualRowForButtonLookup && it.col == colIndex }
                                if (buttonAtPos != null) {
                                    val clicked = buttonAtPos.label in activeButtons
                                    val enabled = !(throwZone == 4 && "Sweet Spot" in activeButtons)
                                    Button(
                                        onClick = { onPress(buttonAtPos.label) },
                                        enabled = enabled,
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = if (clicked) successGreen else greedyPink,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Text(buttonAtPos.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun GreedyControlCard(
    timerRunning: Boolean,
    onToggleTimer: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top row: TIMER and PAUSE buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (!timerRunning) onToggleTimer() },
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
                    onClick = { if (timerRunning) onToggleTimer() },
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

            // Bottom row: EDIT and RESET buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { /* Edit - placeholder */ },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✎", fontSize = 16.sp)
                        Text("EDIT", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF00BCD4), // Cyan
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("↻", fontSize = 16.sp)
                        Text("RESET", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreedyQueueCard(queue: List<GreedyParticipant>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = "Queue (${queue.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(queue) { participant ->
                    val isCurrentTeam = queue.firstOrNull() == participant
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
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

@Composable
private fun GreedyTeamManagementCard(
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onImportClick, modifier = Modifier.weight(1f)) { Text("Import") }
                Button(onClick = onExportClick, modifier = Modifier.weight(1f)) { Text("Export") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportCsvClick, modifier = Modifier.weight(1f)) { Text("CSV") }
                Button(onClick = onAddTeamClick, modifier = Modifier.weight(1f)) { Text("Add") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPreviousParticipant, modifier = Modifier.weight(1f)) { Text("Previous") }
                Button(onClick = onSkipParticipant, modifier = Modifier.weight(1f)) { Text("Skip") }
            }
            Button(onClick = onNextParticipant, modifier = Modifier.fillMaxWidth()) { Text("Next") }
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

private fun greedyTimestamp(now: kotlinx.datetime.LocalDateTime): String {
    fun pad2(n: Int) = n.toString().padStart(2, '0')
    return buildString {
        append(now.year)
        append(pad2(now.monthNumber))
        append(pad2(now.dayOfMonth))
        append('_')
        append(pad2(now.hour))
        append(pad2(now.minute))
        append(pad2(now.second))
    }
}

