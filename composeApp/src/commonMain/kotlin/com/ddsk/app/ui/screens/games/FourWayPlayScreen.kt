package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

object FourWayPlayScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FourWayPlayScreenModel() }
        val score by screenModel.score.collectAsState()
        val quads by screenModel.quads.collectAsState()
        val misses by screenModel.misses.collectAsState()

        var timerRunning by remember { mutableStateOf(false) }
        var secondsRemaining by remember { mutableStateOf(60) }

        val participants by screenModel.participants.collectAsState()
        val sidebarCollapsed by screenModel.sidebarCollapsed.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpot by screenModel.sweetSpotClicked.collectAsState()
        val allRollers by screenModel.allRollers.collectAsState()
        val sidebarMessage by screenModel.lastSidebarAction.collectAsState()

        var showAddParticipant by remember { mutableStateOf(false) }
        var handlerInput by remember { mutableStateOf("") }
        var dogInput by remember { mutableStateOf("") }
        var utnInput by remember { mutableStateOf("") }

        val scope = rememberCoroutineScope()

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
            if (!timerRunning) {
                audioPlayer.stop()
            } else {
                audioPlayer.play()
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val spacing = 12.dp
            val sidebarWidth = if (sidebarCollapsed) 68.dp else 190.dp
            val participantWidth = 220.dp
            val minFieldWidth = 200.dp
            val availableFieldWidth = (maxWidth - sidebarWidth - participantWidth - (spacing * 2)).coerceAtLeast(minFieldWidth)
            val fieldHeight = (availableFieldWidth / 1.2f).coerceAtLeast(320.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                SidebarColumn(
                    collapsed = sidebarCollapsed,
                    onToggle = screenModel::toggleSidebar,
                    onImport = { filePicker.launch() },
                    onExport = { screenModel.recordSidebarAction("Export tapped") },
                    onLog = { screenModel.recordSidebarAction("Log tapped") },
                    onAddTeam = { showAddParticipant = true },
                    onHelp = { screenModel.recordSidebarAction("Help opened") },
                    onReset = screenModel::resetScoring,
                    onFlipField = screenModel::flipField,
                    onPrevious = screenModel::moveToPreviousParticipant,
                    onNext = screenModel::moveToNextParticipant,
                    onSkip = screenModel::skipParticipant,
                    onAllRollers = screenModel::toggleAllRollers,
                    onUndo = screenModel::undo,
                    allRollersEnabled = allRollers,
                    sidebarMessage = sidebarMessage,
                )

                ParticipantPanel(
                    participants = participants,
                    height = fieldHeight,
                    onSelect = { screenModel.recordSidebarAction("Viewing ${it.handler}") },
                )

                FieldGrid(
                    fieldFlipped = fieldFlipped,
                    clickedZones = clickedZones,
                    sweetSpot = sweetSpot,
                    allRollers = allRollers,
                    onZoneClick = screenModel::handleZoneClick,
                    onSweetSpotClick = screenModel::handleSweetSpotClick,
                    modifier = Modifier
                        .width(availableFieldWidth)
                        .height(fieldHeight)
                )
            }
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
private fun HeaderRow(
    timerRunning: Boolean,
    secondsRemaining: Int,
    onTimerToggle: () -> Unit,
    participants: List<FourWayPlayScreenModel.Participant>,
    score: Int,
    quads: Int,
    misses: Int,
    onMiss: () -> Unit,
) {
    Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTimerToggle,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (timerRunning) Palette.warning else Palette.info,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text(if (timerRunning) "$secondsRemaining s" else "Timer", fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participants.firstOrNull()?.let { "${it.handler} & ${it.dog}" } ?: "No team loaded",
                    style = MaterialTheme.typography.h6,
                    color = Palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Score $score  •  Quads $quads  •  Misses $misses",
                    style = MaterialTheme.typography.body2,
                    color = Palette.onSurfaceVariant
                )
            }

            Button(
                onClick = onMiss,
                colors = ButtonDefaults.buttonColors(backgroundColor = Palette.error, contentColor = Color.White),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Miss", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SidebarColumn(
    collapsed: Boolean,
    onToggle: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLog: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
    onFlipField: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAllRollers: () -> Unit,
    onUndo: () -> Unit,
    allRollersEnabled: Boolean,
    sidebarMessage: String,
) {
    val sidebarWidth: Dp = if (collapsed) 68.dp else 190.dp
    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .background(Palette.surfaceContainer, RoundedCornerShape(14.dp))
            .border(1.dp, Palette.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (collapsed) "☰" else "Hide")
        }

        if (!collapsed) {
            Spacer(Modifier.height(12.dp))
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                SidebarButton("Import", Palette.info, onImport)
                SidebarButton("Export", Palette.info, onExport)
                SidebarButton("Log", Palette.info, onLog)
                SidebarButton("Add Team", Palette.success, onAddTeam)
                SidebarButton("Help", Palette.primaryContainer, onHelp, Palette.onPrimaryContainer)
                SidebarButton("Reset", Palette.warning, onReset)
                SidebarButton("Flip Field", Palette.primary, onFlipField)
                SidebarButton("Previous", Palette.tertiary, onPrevious)
                SidebarButton(
                    text = if (allRollersEnabled) "All Rollers✓" else "All Rollers",
                    background = if (allRollersEnabled) Palette.success else Palette.primary,
                    onClick = onAllRollers
                )
                SidebarButton("Undo", Palette.error, onUndo)
                SidebarButton("Next", Palette.tertiary, onNext)
                SidebarButton("Skip", Palette.tertiary, onSkip)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = sidebarMessage,
                    style = MaterialTheme.typography.caption,
                    color = Palette.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarButton(
    text: String,
    background: Color,
    onClick: () -> Unit,
    textColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = background, contentColor = textColor)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ParticipantPanel(
    participants: List<FourWayPlayScreenModel.Participant>,
    height: Dp,
    onSelect: (FourWayPlayScreenModel.Participant) -> Unit,
) {
    Card(
        backgroundColor = Palette.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = height)
            .fillMaxHeight(),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Remaining (${participants.size})", fontWeight = FontWeight.Bold, color = Palette.onSurface)
            Spacer(Modifier.height(8.dp))
            Divider(color = Palette.outlineVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(participants) { participant ->
                    ParticipantRow(participant = participant, onSelect = { onSelect(participant) })
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: FourWayPlayScreenModel.Participant, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.surface)
            .padding(10.dp)
            .then(Modifier)
    ) {
        Text(participant.handler, fontWeight = FontWeight.Bold, color = Palette.onSurface)
        Text(participant.dog, color = Palette.onSurfaceVariant, fontSize = 12.sp)
        Text(participant.utn, color = Palette.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun FieldGrid(
    fieldFlipped: Boolean,
    clickedZones: Set<Int>,
    sweetSpot: Boolean,
    allRollers: Boolean,
    onZoneClick: (Int) -> Unit,
    onSweetSpotClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(18.dp),
            backgroundColor = Palette.surfaceContainer,
            elevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val horizontalSpacing = 8.dp
                val verticalSpacing = 8.dp
                val availableHeight = (maxHeight - verticalSpacing * 2).coerceAtLeast(0.dp)
                val unitHeight = (availableHeight / 3.5f)
                val compactRowHeight = unitHeight.coerceAtLeast(72.dp)
                val middleRowHeight = (unitHeight * 1.5f).coerceAtLeast(96.dp)
                val availableWidth = (maxWidth - horizontalSpacing * 2).coerceAtLeast(0.dp)
                val cellWidth = (availableWidth / 3f).coerceAtLeast(72.dp)

                val rowOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
                val colOrder = if (fieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                ) {
                    rowOrder.forEach { row ->
                        val rowHeight = if (row == 1) middleRowHeight else compactRowHeight
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                        ) {
                            colOrder.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Palette.surface)
                                        .border(1.dp, Palette.outlineVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        row == 0 && col == 0 -> ZoneButton(
                                            value = 1,
                                            clicked = 1 in clickedZones,
                                            onClick = { onZoneClick(1) }
                                        )
                                        row == 0 && col == 2 -> ZoneButton(
                                            value = 2,
                                            clicked = 2 in clickedZones,
                                            onClick = { onZoneClick(2) }
                                        )
                                        row == 2 && col == 2 -> ZoneButton(
                                            value = 3,
                                            clicked = 3 in clickedZones,
                                            onClick = { onZoneClick(3) }
                                        )
                                        row == 2 && col == 0 -> ZoneButton(
                                            value = 4,
                                            clicked = 4 in clickedZones,
                                            onClick = { onZoneClick(4) }
                                        )
                                        row == 1 && col == 1 -> SweetSpotButton(
                                            active = sweetSpot,
                                            onClick = onSweetSpotClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (allRollers) {
            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = "All Rollers Active",
                    color = Palette.success,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ZoneButton(value: Int, clicked: Boolean, onClick: () -> Unit) {
    val colors = if (clicked) {
        ButtonDefaults.buttonColors(backgroundColor = Palette.success, contentColor = Palette.onSuccess)
    } else {
        ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary)
    }
    Button(
        onClick = onClick,
        enabled = !clicked,
        colors = colors,
        modifier = Modifier.fillMaxSize().padding(6.dp),
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
            backgroundColor = if (active) Palette.success else Palette.info,
            contentColor = if (active) Palette.onSuccess else Palette.onInfo
        ),
        modifier = Modifier.fillMaxSize().padding(6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Sweet Spot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private object Palette {
    val primary = Color(0xFF6750A4)
    val onPrimary = Color.White
    val primaryContainer = Color(0xFFEADDFF)
    val onPrimaryContainer = Color(0xFF21005D)
    val success = Color(0xFF00C853)
    val onSuccess = Color.White
    val warning = Color(0xFFFFB74D)
    val info = Color(0xFF2979FF)
    val onInfo = Color.White
    val error = Color(0xFFD50000)
    val background = Color(0xFFFEFBFF)
    val surface = Color(0xFFFEF7FF)
    val surfaceContainer = Color(0xFFF5EFF7)
    val onSurface = Color(0xFF1C1B1F)
    val onSurfaceVariant = Color(0xFF49454F)
    val outlineVariant = Color(0xFFCAC4D0)
    val tertiary = Color(0xFFF500A1)
}
