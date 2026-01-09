package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

// Color Definitions
private object GreedyColors {
    val mdPrimary = Color(0xFF6750A4)
    val mdOnPrimary = Color(0xFFFFFFFF)
    val mdPrimaryContainer = Color(0xFFEADDFF)
    val mdOnPrimaryContainer = Color(0xFF21005D)
    val mdSecondary = Color(0xFF625B71)
    val mdSurface = Color(0xFFFFFBFE)
    val mdOnSurface = Color(0xFF1C1B1F)
    val mdSurfaceContainer = Color(0xFFF5EFF7)
    val mdSurfaceVariant = Color(0xFFE7E0EC)
    val mdOutline = Color(0xFF79747E)
    val mdSuccess = Color(0xFF4CAF50)
    val mdError = Color(0xFFB3261E)
    val mdInfo = Color(0xFF2196F3)
    val mdWarning = Color(0xFFFFB74D) // Using lighter warning for better contrast
    // Vibrant Colors
    val vibTertiary = Color(0xFFF500A1) // Pinkish
}

object GreedyScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<GreedyScreenModel>()

        // State Collections
        val score by screenModel.score.collectAsState()
        val throwZone by screenModel.throwZone.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val activeButtons by screenModel.activeButtons.collectAsState()
        val rotationDegrees by screenModel.rotationDegrees.collectAsState()

        val isClockwiseDisabled by screenModel.isClockwiseDisabled.collectAsState()
        val isCounterClockwiseDisabled by screenModel.isCounterClockwiseDisabled.collectAsState()
        val isRotateStartingZoneVisible by screenModel.isRotateStartingZoneVisible.collectAsState()
        val allRollersEnabled by screenModel.allRollersEnabled.collectAsState()

        val participants by screenModel.participants.collectAsState()
        val allParticipants by screenModel.allParticipants.collectAsState()
        val currentParticipant = participants.firstOrNull()

        // Local UI State
        var isSidebarCollapsed by remember { mutableStateOf(false) }
        var isTimerRunning by remember { mutableStateOf(false) }

        // Modals State
        var showTeamModal by remember { mutableStateOf(false) }
        var showAddTeamModal by remember { mutableStateOf(false) }
        var showSweetSpotModal by remember { mutableStateOf(false) }
        var showHelpModal by remember { mutableStateOf(false) }
        var showConfirmSwitchModal by remember { mutableStateOf(false) }
        var showClearPrompt by remember { mutableStateOf(false) }
        var participantToSwitchIndex by remember { mutableStateOf<Int?>(null) }

        // Scope and Audio
        val scope = rememberCoroutineScope()
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Greedy") })

        val assetLoader = rememberAssetLoader()

        // File Export
        val fileExporter = rememberFileExporter()

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audioPlayer.play() else audioPlayer.stop()
        }

        // File Import
        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }

        // --- UI ---
        Scaffold(
            backgroundColor = GreedyColors.mdSurface
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {

                // 1. Sidebar
                GreedySidebar(
                    isCollapsed = isSidebarCollapsed,
                    onToggleCollapse = { isSidebarCollapsed = !isSidebarCollapsed },
                    onImport = { filePicker.launch() },
                    onExport = {
                        val templateBytes = assetLoader.load("templates/UDC Greedy Data Entry L1 Div Sort.xlsx")
                        if (templateBytes != null) {
                             val xlsxData = screenModel.exportParticipantsAsXlsx(templateBytes)
                             fileExporter.save("Greedy_Export.xlsx", xlsxData)
                        } else {
                             // Fallback to CSV if template fails
                             val csvData = screenModel.exportParticipantsAsCsv().encodeToByteArray()
                             fileExporter.save("Greedy_Export.csv", csvData)
                        }
                    },
                    onPrevious = { screenModel.previousParticipant() },
                    onNext = { screenModel.nextParticipant() },
                    onAddTeam = { showAddTeamModal = true },
                    onSkip = { screenModel.skipParticipant() },
                    onHelp = { showHelpModal = true },
                    onAllRollers = { screenModel.toggleAllRollers() },
                    onReset = { screenModel.reset() },
                    allRollersEnabled = allRollersEnabled,
                    onClearConfirm = { showClearPrompt = true }
                )

                // 2. Main Content Area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (isSidebarCollapsed) 50.dp else 220.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Controls (Team & Timer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Team Display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(GreedyColors.mdSurfaceContainer, RoundedCornerShape(8.dp))
                                .border(1.dp, GreedyColors.mdOutline, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .clickable { showTeamModal = true }
                        ) {
                            Text(
                                "Current Team: ${currentParticipant?.handler ?: "No team loaded"}${if (currentParticipant?.dog?.isNotEmpty() == true) " & ${currentParticipant.dog}" else ""}",
                                style = MaterialTheme.typography.h6,
                                fontWeight = FontWeight.Bold,
                                color = GreedyColors.mdOnSurface
                            )
                            Text(
                                "(Long press to view all teams & scores)",
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timer Button
                        Button(
                            onClick = { isTimerRunning = !isTimerRunning },
                            colors = ButtonDefaults.buttonColors(backgroundColor = if (isTimerRunning) GreedyColors.mdSuccess else GreedyColors.mdInfo),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                if (isTimerRunning) "Stop Timer" else "Start Timer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        InfoBox("Throw Zone: $throwZone")
                        Spacer(modifier = Modifier.width(16.dp))
                        InfoBox("Score: $score")
                        Spacer(modifier = Modifier.width(16.dp))
                        InfoBox("Misses: $misses")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Grid Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Remaining Teams List
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .height(300.dp) // Match grid height roughly
                                .background(GreedyColors.mdSurfaceContainer, RoundedCornerShape(8.dp))
                                .border(1.dp, GreedyColors.mdOutline, RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GreedyColors.mdPrimary, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "Remaining (${if (participants.isNotEmpty()) participants.size - 1 else 0})",
                                    color = GreedyColors.mdOnPrimary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(participants) { index, p ->
                                    val isActive = index == 0
                                    // Skip first participant if we only show remaining
                                    // Actually, remaining usually means everyone in queue.
                                    // Let's show everyone in queue, highlighting active.

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isActive) GreedyColors.mdPrimaryContainer else if (index % 2 == 1) GreedyColors.mdSurface else GreedyColors.mdSurfaceVariant)
                                            .padding(8.dp)
                                            .clickable {
                                                if (!isActive) {
                                                    participantToSwitchIndex = index
                                                    showConfirmSwitchModal = true
                                                }
                                            }
                                    ) {
                                        Text(
                                            p.handler,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) GreedyColors.mdOnPrimaryContainer else GreedyColors.mdOnSurface
                                        )
                                        Text(
                                            p.dog,
                                            style = MaterialTheme.typography.caption,
                                            color = if (isActive) GreedyColors.mdOnPrimaryContainer else Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Center: Rotating Grid
                        GreedyRotatingGrid(
                            activeButtons = activeButtons,
                            rotationDegrees = rotationDegrees.toFloat(),
                            onButtonPress = { screenModel.handleButtonPress(it) },
                            isRectangleLocked = throwZone == 4 && activeButtons.contains("Sweet Spot")
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        // Right: Controls Grid (3x3 layout logic from React)
                        GreedyControlsGrid(
                            throwZone = throwZone,
                            isClockwiseDisabled = isClockwiseDisabled,
                            isCounterClockwiseDisabled = isCounterClockwiseDisabled,
                            isRotateStartingZoneVisible = isRotateStartingZoneVisible,
                            isAnyButtonClicked = activeButtons.isNotEmpty(),
                            onNextThrowZoneClockwise = { screenModel.nextThrowZone(true) },
                            onNextThrowZoneCounterClockwise = { screenModel.nextThrowZone(false) },
                            onRotateStarting = { screenModel.rotateStartingZone() },
                            onSweetSpotBonus = { showSweetSpotModal = true },
                            onMissPlus = { screenModel.incrementMisses() },
                            onUndo = { screenModel.undo() }
                        )
                    }
                }

                // Home Button (Top Left)
                IconButton(
                    onClick = { navigator.pop() },
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp)
                        .background(GreedyColors.mdPrimary, RoundedCornerShape(24.dp))
                ) {
                    Text("🏠", fontSize = 24.sp)
                }
            }
        }

        // --- Modals ---

        if (showAddTeamModal) {
            AddTeamModal(
                onDismiss = { showAddTeamModal = false },
                onAdd = { h, d, u -> screenModel.addParticipant(h, d, u) }
            )
        }

        if (showSweetSpotModal) {
            SweetSpotBonusModal(
                onDismiss = { showSweetSpotModal = false },
                onConfirm = { screenModel.setSweetSpotBonus(it) }
            )
        }

        if (showTeamModal) {
            TeamListModal(
                participants = allParticipants,
                onDismiss = { showTeamModal = false }
            )
        }

        if (showConfirmSwitchModal && participantToSwitchIndex != null) {
            AlertDialog(
                onDismissRequest = { showConfirmSwitchModal = false },
                title = { Text("Switch Current Team?") },
                text = { Text("Any unsaved data for the current round will be lost.") },
                confirmButton = {
                    Button(onClick = {
                        screenModel.selectParticipant(participantToSwitchIndex!!)
                        showConfirmSwitchModal = false
                    }) { Text("Yes, Switch") }
                },
                dismissButton = {
                    Button(onClick = { showConfirmSwitchModal = false }) { Text("Cancel") }
                }
            )
        }

        if (showClearPrompt) {
            AlertDialog(
                onDismissRequest = { showClearPrompt = false },
                title = { Text("Clear All Participants?") },
                text = { Text("Are you sure you want to clear all participants? This will remove all loaded teams and their data.") },
                confirmButton = {
                    Button(
                        onClick = {
                            screenModel.clearParticipants()
                            showClearPrompt = false
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdError, contentColor = Color.White)
                    ) { Text("Yes, Clear") }
                },
                dismissButton = {
                    Button(onClick = { showClearPrompt = false }) { Text("Cancel") }
                }
            )
        }

        if (showHelpModal) {
            HelpModal(onDismiss = { showHelpModal = false })
        }
    }
}

// --- Sub-Composables ---

@Composable
fun InfoBox(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.h6,
        fontWeight = FontWeight.Bold,
        color = GreedyColors.mdOnSurface,
        modifier = Modifier
            .background(GreedyColors.mdSurfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, GreedyColors.mdOutline, RoundedCornerShape(8.dp))
            .padding(12.dp)
    )
}

@Composable
fun GreedySidebar(
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddTeam: () -> Unit,
    onSkip: () -> Unit,
    onHelp: () -> Unit,
    onAllRollers: () -> Unit,
    onReset: () -> Unit,
    allRollersEnabled: Boolean,
    onClearConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(if (isCollapsed) 40.dp else 200.dp)
            .background(MaterialTheme.colors.surface) // Transparent/Surface
            .padding(vertical = 120.dp, horizontal = 4.dp), // Check logic for padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Toggle Button
        Button(
            onClick = onToggleCollapse,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A90E2))
        ) {
            Text(if (isCollapsed) "→" else "←", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!isCollapsed) {
            // Import/Export
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                // Import with Long Press support
                LongPressSidebarButton(
                    text = "Import",
                    color = GreedyColors.mdInfo,
                    modifier = Modifier.weight(1f),
                    onClick = onImport,
                    onLongClick = onClearConfirm
                )

                SidebarButton("Export", GreedyColors.mdWarning, Modifier.weight(1f), onClick = onExport)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Prev/Next
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarButton("Previous", GreedyColors.mdPrimary, Modifier.weight(1f), onClick = onPrevious)
                SidebarButton("Next", GreedyColors.mdPrimary, Modifier.weight(1f), onClick = onNext)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Add/Skip
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarButton("Add Team", GreedyColors.mdSuccess, Modifier.weight(1f), onClick = onAddTeam)
                SidebarButton("Skip", GreedyColors.mdPrimary, Modifier.weight(1f), onClick = onSkip)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Help
            SidebarButton("Help", Color(0xFF2E7D32), Modifier.fillMaxWidth(), onClick = onHelp)
            Spacer(modifier = Modifier.height(8.dp))

            // All Rollers / Reset
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SidebarButton(
                    "All Rollers",
                    if (allRollersEnabled) GreedyColors.mdSuccess else GreedyColors.mdError,
                    Modifier.weight(1f),
                    onClick = onAllRollers
                )
                SidebarButton("Reset", GreedyColors.mdError, Modifier.weight(1f), onClick = onReset)
            }
        }
    }
}

@Composable
fun SidebarButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        contentPadding = PaddingValues(4.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressSidebarButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(color, MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun TeamListModal(participants: List<GreedyScreenModel.GreedyParticipant>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All Participants") },
        text = {
            Column(Modifier.fillMaxHeight(0.8f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.border(1.dp, Color.Gray)) {
                    item {
                        Row(Modifier.background(Color.LightGray).padding(8.dp)) {
                            Text("Handler", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("Dog", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("Score", Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
                        }
                    }
                    itemsIndexed(participants) { _, p ->
                        Row(Modifier.padding(8.dp)) {
                            Text(p.handler, Modifier.weight(1f))
                            Text(p.dog, Modifier.weight(1f))
                            Text(p.score.toString(), Modifier.weight(0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun HelpModal(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Greedy Help") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                Text("""
                    Top controls:
                    - Current Team display: Long-press to view full participants table.
                    - Timer button: Starts/stops 75s timer.

                    Info row:
                    - Displays current Throw Zone, Score, and Misses.

                    Sidebar:
                    - "Import": Opens file picker to import participants. Long-press to CLEAR all teams.
                    - "Export": Exports participant scores to CSV.
                    - "Add Team": Manually add a participant.
                    - "Previous"/"Next": Navigate teams.
                    - "Skip": Move current team to the end of the list.
                    - "All Rollers": Toggles all rollers bonus flag.
                    - "Reset": Resets current round score.

                    Grid buttons:
                    - Record catches in zones 1-4.
                    - X, Y, Z buttons record successful catches.
                    - "Sweet Spot": Only in zone 4. Bonus points.

                    Controls:
                    - "Next ThrowZone": Advances zone (Clockwise/Counter-Clockwise).
                    - "Rotate Start": Rotates starting orientation (Zone 1 only).
                    - "Sweet Spot Bonus": Manually add bonus points (1-8).
                    - "Miss+": Record a miss.
                    - "Undo": Revert last action.
                """.trimIndent())
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun GreedyRotatingGrid(
    activeButtons: Set<String>,
    rotationDegrees: Float,
    onButtonPress: (String) -> Unit,
    isRectangleLocked: Boolean
) {
    val boxSize = 300.dp

    Box(
        modifier = Modifier
            .size(boxSize)
            .rotate(rotationDegrees)
            .background(GreedyColors.mdSurfaceContainer, RoundedCornerShape(12.dp))
            .border(1.5.dp, GreedyColors.mdOutline, RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(3) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(3) { col ->
                        val buttonName = when {
                            row == 0 && col == 0 -> "X"
                            row == 0 && col == 1 -> "Y"
                            row == 0 && col == 2 -> "Z"
                            row == 1 && col == 1 -> "Sweet Spot"
                            else -> null
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, GreedyColors.mdOutline)
                                .padding(2.dp)
                        ) {
                            if (buttonName != null) {
                                val isActive = activeButtons.contains(buttonName)
                                Button(
                                    onClick = { onButtonPress(buttonName) },
                                    enabled = if (buttonName == "Sweet Spot") true else !isRectangleLocked, // Sweet Spot never disabled? React: "Sweet Spot button is never disabled"
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (isActive) GreedyColors.mdSuccess else GreedyColors.vibTertiary,
                                        contentColor = Color.White,
                                        disabledBackgroundColor = GreedyColors.mdSuccess.copy(alpha = 0.5f) // Keep visual style if locked but active?
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        buttonName,
                                        modifier = Modifier.rotate(-rotationDegrees), // Counter-rotate text
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
fun GreedyControlsGrid(
    throwZone: Int,
    isClockwiseDisabled: Boolean,
    isCounterClockwiseDisabled: Boolean,
    isRotateStartingZoneVisible: Boolean,
    isAnyButtonClicked: Boolean,
    onNextThrowZoneClockwise: () -> Unit,
    onNextThrowZoneCounterClockwise: () -> Unit,
    onRotateStarting: () -> Unit,
    onSweetSpotBonus: () -> Unit,
    onMissPlus: () -> Unit,
    onUndo: () -> Unit
) {
    val boxSize = 300.dp

    // Grid 3x3 layout
    Column(Modifier.size(boxSize)) {
        // Row 0
        Row(Modifier.weight(1f)) {
            // Col 0: Counter Clockwise
            Box(Modifier.weight(1f).padding(4.dp)) {
                val enabled = !isCounterClockwiseDisabled && isAnyButtonClicked && throwZone < 4
                // Always layout, but maybe hidden/disabled logic visually
                Button(
                    onClick = onNextThrowZoneCounterClockwise,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("<-- Speed", color = Color.White, textAlign = TextAlign.Center) // React: "<-- Next ThrowZone"
                }
            }
            // Col 1: Clockwise
            Box(Modifier.weight(1f).padding(4.dp)) {
                val enabled = !isClockwiseDisabled && isAnyButtonClicked && throwZone < 4
                Button(
                    onClick = onNextThrowZoneClockwise,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Speed -->", color = Color.White, textAlign = TextAlign.Center) // React: "Next ThrowZone -->"
                }
            }
            // Col 2: Rotate Starting
            Box(Modifier.weight(1f).padding(4.dp)) {
                if (throwZone == 1 && isRotateStartingZoneVisible) {
                    Button(
                        onClick = onRotateStarting,
                        colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Rotate Start", color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Row 1
        Row(Modifier.weight(1f)) {
            // Col 0: Sweet Spot Bonus
            Box(Modifier.weight(1f).padding(4.dp)) {
                Button(
                    onClick = onSweetSpotBonus,
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Bonus", color = Color.White)
                }
            }
            // Col 1: Miss+
            Box(Modifier.weight(1f).padding(4.dp)) {
                Button(
                    onClick = onMissPlus,
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Miss+", color = Color.White)
                }
            }
            // Col 2: Empty
            Spacer(Modifier.weight(1f))
        }

        // Row 2
        Row(Modifier.weight(1f)) {
            // Col 0: Undo
            Box(Modifier.weight(1f).padding(4.dp)) {
                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(backgroundColor = GreedyColors.mdSecondary),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Undo", color = Color.White)
                }
            }
            // Col 1 & 2: Empty
            Spacer(Modifier.weight(2f))
        }
    }
}

@Composable
fun AddTeamModal(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Participant") },
        text = {
            Column {
                OutlinedTextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") })
                OutlinedTextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") })
                OutlinedTextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (handler.isNotBlank() && dog.isNotBlank() && utn.isNotBlank()) {
                    onAdd(handler, dog, utn)
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SweetSpotBonusModal(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Sweet Spot Bonus") },
        text = {
            Column {
                Text("Enter a number from 1 to 8:")
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 1 && it.all { char -> char.isDigit() }) input = it },
                    label = { Text("Bonus") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val bonus = input.toIntOrNull()
                if (bonus != null) {
                    onConfirm(bonus)
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

