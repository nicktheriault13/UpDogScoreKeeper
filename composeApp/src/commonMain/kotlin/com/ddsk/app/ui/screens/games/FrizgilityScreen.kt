package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.ui.screens.games.FrizgilityPalette.mdBackground
import com.ddsk.app.ui.screens.games.FrizgilityPalette.mdOnBackground
import com.ddsk.app.ui.screens.games.FrizgilityPalette.mdPrimary
import com.ddsk.app.ui.screens.games.FrizgilityPalette.mdPrimaryContainer
import com.ddsk.app.ui.screens.games.FrizgilityPalette.mdSurface
import com.ddsk.app.ui.screens.games.FrizgilityPalette.vibrantError
import com.ddsk.app.ui.screens.games.FrizgilityPalette.vibrantInfo
import com.ddsk.app.ui.screens.games.FrizgilityPalette.vibrantPrimary
import com.ddsk.app.ui.screens.games.FrizgilityPalette.vibrantSuccess
import com.ddsk.app.ui.screens.games.FrizgilityPalette.vibrantWarning
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object FrizgilityScreen : Screen, KoinComponent {

    @Composable
    override fun Content() {
        val screenModel = get<FrizgilityScreenModel>()
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val logEntries by screenModel.logEntries.collectAsState()

        var showAddTeam by rememberSaveable { mutableStateOf(false) }
        var showClearPrompt by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        var showLog by rememberSaveable { mutableStateOf(false) }
        var exportPreview by remember { mutableStateOf<String?>(null) }

        Surface(color = mdBackground, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Frizgility",
                    style = MaterialTheme.typography.h4,
                    color = mdOnBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FrizgilitySidebar(
                        collapsed = uiState.sidebarCollapsed,
                        onToggle = { screenModel.toggleSidebar() },
                        onImportSample = {
                            screenModel.importParticipantsFromCsv(SAMPLE_PARTICIPANTS_CSV)
                        },
                        onExportSnapshot = {
                            exportPreview = screenModel.exportParticipantsSnapshot()
                        },
                        onResetGame = { screenModel.resetGame() },
                        onAddTeam = { showAddTeam = true },
                        onHelp = { showHelp = true },
                        onLog = { showLog = true },
                        onClearParticipants = { showClearPrompt = true }
                    )
                    ParticipantList(
                        participants = uiState.participants,
                        activeIndex = uiState.activeParticipantIndex
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ScoreRow(
                            uiState = uiState,
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartTimer = { screenModel.startTimer() },
                            onStopTimer = { screenModel.stopTimer() },
                            onResetTimer = { screenModel.resetTimer() },
                            onToggleSweetSpot = { screenModel.toggleSweetSpot() },
                            onUndo = { screenModel.undo() },
                            onNext = { screenModel.nextParticipant() },
                            onSkip = { screenModel.skipParticipant() },
                            onAllRollers = { screenModel.toggleAllRollers() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FrizgilityGrid(
                            uiState = uiState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onObstacle = { lane -> screenModel.handleObstacleClick(lane) },
                            onFail = { lane -> screenModel.handleFailClick(lane) },
                            onCatch = { points -> screenModel.handleCatchClick(points) },
                            onMiss = { screenModel.handleMissClick() }
                        )
                    }
                }
            }
        }

        if (showAddTeam) {
            AddTeamDialog(
                onAdd = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    showAddTeam = false
                },
                onDismiss = { showAddTeam = false }
            )
        }

        if (showClearPrompt) {
            ConfirmDialog(
                title = "Clear Participant Queue?",
                text = "This removes every queued team and cannot be undone.",
                confirmLabel = "Clear",
                confirmColor = vibrantError,
                onConfirm = {
                    screenModel.clearParticipants()
                    showClearPrompt = false
                },
                onDismiss = { showClearPrompt = false }
            )
        }

        if (showHelp) {
            InfoDialog(
                title = "Help — Button Functions",
                text = HELP_TEXT,
                onDismiss = { showHelp = false }
            )
        }

        if (showLog) {
            InfoDialog(
                title = "Recent Log",
                text = logEntries.take(40).joinToString("\n"),
                onDismiss = { showLog = false }
            )
        }

        exportPreview?.let { csv ->
            InfoDialog(
                title = "Export Snapshot",
                text = csv,
                onDismiss = { exportPreview = null }
            )
        }
    }
}

@Composable
private fun FrizgilitySidebar(
    collapsed: Boolean,
    onToggle: () -> Unit,
    onImportSample: () -> Unit,
    onExportSnapshot: () -> Unit,
    onResetGame: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit,
    onLog: () -> Unit,
    onClearParticipants: () -> Unit,
) {
    Column(
        modifier = Modifier.width(if (collapsed) 72.dp else 180.dp)
            .background(mdSurface, RoundedCornerShape(12.dp))
            .border(1.dp, mdPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(onClick = onToggle) {
            Text(if (collapsed) "☰" else "Hide", fontWeight = FontWeight.Bold)
        }
        if (!collapsed) {
            Spacer(Modifier.height(12.dp))
            SidebarButton(text = "Import", color = vibrantSuccess, onClick = onImportSample)
            SidebarButton(text = "Export", color = vibrantInfo, onClick = onExportSnapshot)
            SidebarButton(text = "Add Team", color = vibrantPrimary, onClick = onAddTeam)
            SidebarButton(text = "Help", color = mdPrimary, onClick = onHelp)
            SidebarButton(text = "Log", color = mdPrimaryContainer, onClick = onLog)
            SidebarButton(text = "Reset", color = vibrantWarning, onClick = onResetGame)
            SidebarButton(text = "Clear Queue", color = vibrantError, onClick = onClearParticipants)
        }
    }
}

@Composable
private fun SidebarButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color)
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ParticipantList(participants: List<FrizgilityParticipant>, activeIndex: Int) {
    Column(
        modifier = Modifier.width(220.dp)
            .fillMaxHeight()
            .background(mdSurface, RoundedCornerShape(12.dp))
            .border(1.dp, mdPrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
    ) {
        Text(
            text = "Teams (${participants.size})",
            modifier = Modifier.fillMaxWidth().background(mdPrimary).padding(8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (participants.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No teams loaded", color = mdOnBackground.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                itemsIndexed(participants) { index, team ->
                    val isActive = index == activeIndex
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (isActive) mdPrimaryContainer.copy(alpha = 0.6f) else mdBackground,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${team.handler} & ${team.dog}",
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = mdOnBackground
                        )
                        Text(text = "UTN: ${team.utn}", fontSize = 11.sp, color = mdOnBackground.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(
    uiState: FrizgilityUiState,
    timerRunning: Boolean,
    timeLeft: Int,
    onStartTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleSweetSpot: () -> Unit,
    onUndo: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAllRollers: () -> Unit
) {
    val participantName = uiState.activeParticipant?.let { "${it.handler} & ${it.dog}" } ?: "No team"
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(mdSurface, RoundedCornerShape(16.dp))
            .border(1.dp, mdPrimary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "Current Team", color = mdOnBackground.copy(alpha = 0.7f), fontSize = 13.sp)
                Text(text = participantName, color = mdOnBackground, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TimerButton(timerRunning = timerRunning, timeLeft = timeLeft, onStartTimer = onStartTimer, onStopTimer = onStopTimer)
                IconButton(onClick = onResetTimer) {
                    Icon(Icons.Default.Close, contentDescription = "Reset Timer", tint = mdOnBackground)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ScoreCard(
                title = "Score",
                value = uiState.scoreBreakdown.totalScore.toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreCard(
                title = "Obstacles",
                value = ((uiState.counters.obstacle1 + uiState.counters.obstacle2 + uiState.counters.obstacle3) * 5).toString(),
                modifier = Modifier.weight(1f)
            )
            ScoreCard(
                title = "Catches",
                value = ((uiState.counters.catch3to10 * 3) + (uiState.counters.catch10plus * 10)).toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ControlChip(
                text = if (uiState.sweetSpotActive) "Sweet Spot ✔" else "Sweet Spot",
                color = vibrantSuccess,
                onClick = onToggleSweetSpot,
                modifier = Modifier.weight(1f)
            )
            ControlChip(text = "Undo", color = mdPrimary, onClick = onUndo, modifier = Modifier.weight(1f))
            ControlChip(text = "Skip", color = vibrantWarning, onClick = onSkip, modifier = Modifier.weight(1f))
            ControlChip(text = "Next", color = vibrantInfo, onClick = onNext, modifier = Modifier.weight(1f))
            ControlChip(
                text = if (uiState.allRollersEnabled) "All Rollers ✔" else "All Rollers",
                color = vibrantPrimary,
                onClick = onAllRollers,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimerButton(timerRunning: Boolean, timeLeft: Int, onStartTimer: () -> Unit, onStopTimer: () -> Unit) {
    Button(
        onClick = { if (timerRunning) onStopTimer() else onStartTimer() },
        colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) vibrantError else vibrantSuccess)
    ) {
        val label = if (timerRunning) "Stop ${timeLeft}s" else "Start"
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScoreCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(mdPrimaryContainer.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, color = mdOnBackground.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(value, color = mdOnBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun ControlChip(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = modifier
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FrizgilityGrid(
    uiState: FrizgilityUiState,
    modifier: Modifier,
    onObstacle: (Int) -> Unit,
    onFail: (Int) -> Unit,
    onCatch: (Int) -> Unit,
    onMiss: () -> Unit
) {
    BoxWithConstraints(modifier = modifier.background(mdSurface, RoundedCornerShape(18.dp)).border(1.dp, mdPrimary.copy(alpha = 0.15f), RoundedCornerShape(18.dp)).padding(12.dp)) {
        val cellWidth = maxWidth / 5
        val cellHeight = maxHeight / 3
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(3) { row ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(5) { col ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, mdPrimary.copy(alpha = 0.2f))
                        )
                    }
                }
            }
        }

        GridOverlayButton(
            label = "Obstacle 1",
            value = uiState.counters.obstacle1,
            enabled = uiState.obstaclePhaseActive && 1 !in uiState.laneLocks,
            color = vibrantPrimary,
            modifier = Modifier.offset(0.dp, 0.dp).size(width = cellWidth * 2, height = cellHeight * 2),
            onClick = { onObstacle(1) }
        )
        GridOverlayButton(
            label = "Obstacle 2",
            value = uiState.counters.obstacle2,
            enabled = uiState.obstaclePhaseActive && 2 !in uiState.laneLocks,
            color = vibrantPrimary.copy(green = 0.5f),
            modifier = Modifier.offset(x = cellWidth * 1, y = 0.dp).size(width = cellWidth * 2, height = cellHeight * 2),
            onClick = { onObstacle(2) }
        )
        GridOverlayButton(
            label = "Obstacle 3",
            value = uiState.counters.obstacle3,
            enabled = uiState.obstaclePhaseActive && 3 !in uiState.laneLocks,
            color = vibrantSuccess,
            modifier = Modifier.offset(x = cellWidth * 2, y = 0.dp).size(width = cellWidth * 2, height = cellHeight * 2),
            onClick = { onObstacle(3) }
        )
        GridOverlayButton(
            label = "3-10 Catch",
            value = uiState.counters.catch3to10,
            enabled = uiState.catchPhaseActive && 3 !in uiState.catchLocks,
            color = vibrantInfo,
            modifier = Modifier.offset(x = cellWidth * 3, y = 0.dp).size(width = cellWidth * 2, height = cellHeight),
            onClick = { onCatch(3) }
        )
        GridOverlayButton(
            label = "10+ Catch",
            value = uiState.counters.catch10plus,
            enabled = uiState.catchPhaseActive && 10 !in uiState.catchLocks,
            color = vibrantInfo.copy(red = 0.6f),
            modifier = Modifier.offset(x = cellWidth * 3, y = cellHeight).size(width = cellWidth * 2, height = cellHeight),
            onClick = { onCatch(10) }
        )
        GridOverlayButton(
            label = "Miss",
            value = uiState.counters.miss,
            enabled = uiState.missClicksInPhase < 3,
            color = vibrantError,
            modifier = Modifier.offset(x = cellWidth * 3, y = cellHeight * 2).size(width = cellWidth * 2, height = cellHeight),
            onClick = onMiss
        )
        GridOverlayButton(
            label = "Fail 1",
            value = uiState.counters.fail1,
            enabled = uiState.obstaclePhaseActive && 1 !in uiState.laneLocks,
            color = vibrantWarning,
            modifier = Modifier.offset(y = cellHeight * 2).size(width = cellWidth, height = cellHeight),
            onClick = { onFail(1) }
        )
        GridOverlayButton(
            label = "Fail 2",
            value = uiState.counters.fail2,
            enabled = uiState.obstaclePhaseActive && 2 !in uiState.laneLocks,
            color = vibrantWarning.copy(green = 0.4f),
            modifier = Modifier.offset(x = cellWidth * 1, y = cellHeight * 2).size(width = cellWidth, height = cellHeight),
            onClick = { onFail(2) }
        )
        GridOverlayButton(
            label = "Fail 3",
            value = uiState.counters.fail3,
            enabled = uiState.obstaclePhaseActive && 3 !in uiState.laneLocks,
            color = vibrantWarning.copy(red = 0.8f),
            modifier = Modifier.offset(x = cellWidth * 2, y = cellHeight * 2).size(width = cellWidth, height = cellHeight),
            onClick = { onFail(3) }
        )
    }
}

@Composable
private fun GridOverlayButton(
    label: String,
    value: Int,
    enabled: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(backgroundColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(value.toString(), color = contentColor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AddTeamDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var handler by rememberSaveable { mutableStateOf("") }
    var dog by rememberSaveable { mutableStateOf("") }
    var utn by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") })
                TextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") })
                TextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (handler.isNotBlank() && dog.isNotBlank()) {
                    onAdd(handler, dog, utn)
                    handler = ""
                    dog = ""
                    utn = ""
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(backgroundColor = confirmColor)) {
                Text(confirmLabel, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.height(280.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f))) {
                LazyColumn { item { Text(text) } }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private const val HELP_TEXT = """
Frizgility — Button functions
\nTop controls manage the timer, score, undo, and participant navigation. Use the sidebar to import or clear teams, open help, view logs, or reset the round. The scoring grid mirrors the course layout: tap obstacle lanes during the obstacle phase, use fail buttons for penalties, switch to catch buttons when illuminated, and tap Miss for dropped discs. Sweet Spot and All Rollers toggle bonus scoring just like the React version.
"""

private const val SAMPLE_PARTICIPANTS_CSV = """
Handler,Dog,UTN
Alex,Nova,UTN-001
Blair,Zelda,UTN-002
Casey,Milo,UTN-003
Dana,Bolt,UTN-004
"""

private object FrizgilityPalette {
    val mdPrimary = Color(0xFF6750A4)
    val mdPrimaryContainer = Color(0xFFEADDFF)
    val mdSurface = Color(0xFFFFFBFE)
    val mdBackground = Color(0xFFF5EFF7)
    val mdOnBackground = Color(0xFF1C1B1F)
    val vibrantPrimary = Color(0xFF2979FF)
    val vibrantSuccess = Color(0xFF00C853)
    val vibrantWarning = Color(0xFFFF9100)
    val vibrantError = Color(0xFFD50000)
    val vibrantInfo = Color(0xFF00B8D4)
}
