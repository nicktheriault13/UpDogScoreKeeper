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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val boomPink = Color(0xFFF500A1)
private val disabledBackground = Color(0xFFF1F1F1)
private val disabledContent = Color(0xFF222222)

private data class ButtonPalette(val background: Color, val content: Color)

private val scoringBasePalette = mapOf(
    BoomScoringButton.One to ButtonPalette(Color(0xFF2979FF), Color.White),
    BoomScoringButton.TwoA to ButtonPalette(Color(0xFF00B8D4), Color.White),
    BoomScoringButton.TwoB to ButtonPalette(Color(0xFF00B8D4), Color.White),
    BoomScoringButton.Five to ButtonPalette(Color(0xFFF500A1), Color.White),
    BoomScoringButton.Ten to ButtonPalette(Color(0xFFFFB74D), Color(0xFF442800)),
    BoomScoringButton.Twenty to ButtonPalette(Color(0xFF2979FF), Color.White),
    BoomScoringButton.TwentyFive to ButtonPalette(Color(0xFFF500A1), Color.White),
    BoomScoringButton.ThirtyFive to ButtonPalette(Color(0xFFD50000), Color.White)
)

private fun scoringPaletteFor(button: BoomScoringButton, clicked: Boolean, enabled: Boolean): ButtonPalette {
    return when {
        clicked -> ButtonPalette(successGreen, Color.White)
        enabled -> scoringBasePalette[button] ?: ButtonPalette(Color(0xFF2979FF), Color.White)
        else -> ButtonPalette(disabledBackground, disabledContent)
    }
}

object BoomScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { BoomScreenModel() }
        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val dialogState = remember { mutableStateOf<BoomDialogState>(BoomDialogState.None) }
        val activeDialog by dialogState
        var csvBuffer by remember { mutableStateOf("") }
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

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Boom") })

        LaunchedEffect(timerRunning) {
            if (timerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnSpacing = 16.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(columnSpacing),
                    horizontalArrangement = Arrangement.spacedBy(columnSpacing)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        ScoreSummaryCard(uiState)
                        Box(modifier = Modifier.weight(1f)) {
                            BoomGrid(
                                screenModel = screenModel,
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ControlRow(screenModel = screenModel)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        TimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartStop = {
                                if (timerRunning) screenModel.stopTimer() else screenModel.startTimer(duration = 60)
                            },
                            onReset = screenModel::resetTimer
                        )
                        ParticipantQueueCard(uiState = uiState)
                        ImportExportCard(
                            onImportClick = { filePicker.launch() },
                            onExportClick = {
                                csvBuffer = screenModel.exportParticipantsAsCsv()
                                dialogState.value = BoomDialogState.Export
                            }
                        )
                    }
                }
            }
        }

        if (activeDialog is BoomDialogState.Export) {
            CsvExportDialog(
                csvText = csvBuffer,
                onDismiss = { dialogState.value = BoomDialogState.None }
            )
        }
    }
}

@Composable
private fun ScoreSummaryCard(uiState: BoomUiState) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val score = uiState.scoreBreakdown.totalScore
            Text(text = "Score: $score", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            Text(
                text = buildString {
                    append("Throws: ${uiState.scoreBreakdown.throwsCompleted}")
                    append(" • Duds: ${uiState.scoreBreakdown.duds}")
                    append(" • Sweet Spots: ${uiState.scoreBreakdown.sweetSpotAwards}")
                },
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            Text(
                text = "Last Throw: ${uiState.scoreBreakdown.lastThrowPoints} pts",
                style = MaterialTheme.typography.body2
            )
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            val active = uiState.activeParticipant
            Text(
                text = active?.let { "Active Team: ${it.handler} & ${it.dog} (${it.utn})" } ?: "No active team",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BoomGrid(
    screenModel: BoomScreenModel,
    uiState: BoomUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = 8.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 16.dp
            val interCellSpacing = 12.dp
            val gridWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
            val widthLimitedCell = ((gridWidth - interCellSpacing * 4) / 5).coerceAtLeast(40.dp)
            val hasFiniteHeight = maxHeight != Dp.Unspecified && maxHeight != Dp.Infinity && maxHeight > 0.dp
            val heightLimitedCell = if (hasFiniteHeight) {
                val totalSpacing = interCellSpacing * 2
                (((maxHeight - totalSpacing) / 3.8f).coerceAtLeast(36.dp))
            } else {
                widthLimitedCell
            }
            val cellSize = minOf(widthLimitedCell, heightLimitedCell)
            val rowOrder = if (uiState.isFieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
            val columnOrder = if (uiState.isFieldFlipped) listOf(4, 3, 2, 1, 0) else listOf(0, 1, 2, 3, 4)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(interCellSpacing)
            ) {
                rowOrder.forEach { rowIndex ->
                    val rowHeight = if (rowIndex == 1) cellSize * 1.8f else cellSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(interCellSpacing)
                    ) {
                        columnOrder.forEach { colIndex ->
                            when (val cell = gridCellContent(rowIndex, colIndex)) {
                                is GridCellContent.Score -> {
                                    ScoringButtonCell(
                                        button = cell.button,
                                        uiState = uiState,
                                        onClick = screenModel::handleScoringButtonClick
                                    )
                                }
                                GridCellContent.Dud -> {
                                    BoomActionButton(
                                        label = "Dud",
                                        color = warningOrange,
                                        onClick = screenModel::handleDud
                                    )
                                }
                                GridCellContent.Boom -> {
                                    BoomActionButton(
                                        label = "Boom!",
                                        color = boomPink,
                                        onClick = screenModel::handleBoom
                                    )
                                }
                                GridCellContent.SweetSpot -> {
                                    SweetSpotCell(uiState = uiState, onToggle = screenModel::toggleSweetSpot)
                                }
                                GridCellContent.Empty -> {
                                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
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
private fun RowScope.ScoringButtonCell(
    button: BoomScoringButton,
    uiState: BoomUiState,
    onClick: (BoomScoringButton) -> Unit
) {
    val clicked = button.id in uiState.buttonState.clickedButtons
    val enabled = button.id in uiState.buttonState.enabledButtons && !clicked
    val palette = scoringPaletteFor(button, clicked, enabled)
    val colors = ButtonDefaults.buttonColors(
        backgroundColor = palette.background,
        contentColor = palette.content,
        disabledBackgroundColor = palette.background,
        disabledContentColor = palette.content
    )
    Button(
        onClick = { onClick(button) },
        enabled = enabled,
        colors = colors,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = button.label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun RowScope.BoomActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = Color.White),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.SweetSpotCell(uiState: BoomUiState, onToggle: () -> Unit) {
    val isActive = uiState.sweetSpotActive
    Button(
        onClick = onToggle,
        colors = if (isActive) ButtonDefaults.buttonColors(backgroundColor = successGreen, contentColor = Color.White) else ButtonDefaults.buttonColors(),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text(text = if (isActive) "Sweet Spot\nON" else "Sweet Spot", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlRow(screenModel: BoomScreenModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = screenModel::resetGame, modifier = Modifier.weight(1f)) {
            Text("Reset Game")
        }
        Button(onClick = screenModel::nextParticipant, modifier = Modifier.weight(1f)) {
            Text("Next Team")
        }
        Button(onClick = screenModel::skipParticipant, modifier = Modifier.weight(1f)) {
            Text("Skip Team")
        }
        Button(onClick = screenModel::toggleFieldOrientation, modifier = Modifier.weight(1f)) {
            Text("Flip Field")
        }
    }
}

@Composable
private fun TimerCard(timerRunning: Boolean, timeLeft: Int, onStartStop: () -> Unit, onReset: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Timer", style = MaterialTheme.typography.h6)
            Text(text = "Remaining: ${timeLeft}s", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartStop, modifier = Modifier.weight(1f)) {
                    Text(if (timerRunning) "Stop" else "Start 60s")
                }
                Button(onClick = onReset, enabled = !timerRunning && timeLeft > 0, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
private fun ParticipantQueueCard(uiState: BoomUiState) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Teams (${uiState.queue.size + (uiState.activeParticipant?.let { 1 } ?: 0)})", style = MaterialTheme.typography.h6)
            val active = uiState.activeParticipant
            if (active != null) {
                Text(text = "Now Playing: ${active.handler} & ${active.dog}", fontWeight = FontWeight.Bold)
            } else {
                Text(text = "No active participant", fontStyle = MaterialTheme.typography.body2.fontStyle)
            }
            Spacer(modifier = Modifier.heightIn(min = 8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(uiState.queue) { participant ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = "${participant.handler} & ${participant.dog}")
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
            Text(text = "Participants", style = MaterialTheme.typography.h6)
            Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
                Text("Import")
            }
            Button(onClick = onExportClick, modifier = Modifier.fillMaxWidth()) {
                Text("Export CSV")
            }
        }
    }
}

@Composable
private fun CsvExportDialog(csvText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Participants") },
        text = {
            OutlinedTextField(
                value = csvText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private sealed interface GridCellContent {
    data class Score(val button: BoomScoringButton) : GridCellContent
    object Dud : GridCellContent
    object Boom : GridCellContent
    object SweetSpot : GridCellContent
    object Empty : GridCellContent
}

private fun gridCellContent(row: Int, col: Int): GridCellContent = when {
    row == 0 && col == 0 -> GridCellContent.Score(BoomScoringButton.One)
    row == 0 && col == 1 -> GridCellContent.Score(BoomScoringButton.TwoB)
    row == 0 && col == 2 -> GridCellContent.Score(BoomScoringButton.Ten)
    row == 0 && col == 3 -> GridCellContent.Score(BoomScoringButton.TwentyFive)
    row == 1 && col == 0 -> GridCellContent.Dud
    row == 1 && col == 1 -> GridCellContent.Boom
    row == 1 && col == 2 -> GridCellContent.SweetSpot
    row == 1 && col == 4 -> GridCellContent.Score(BoomScoringButton.ThirtyFive)
    row == 2 && col == 1 -> GridCellContent.Score(BoomScoringButton.TwoA)
    row == 2 && col == 2 -> GridCellContent.Score(BoomScoringButton.Five)
    row == 2 && col == 3 -> GridCellContent.Score(BoomScoringButton.Twenty)
    else -> GridCellContent.Empty
}

private sealed interface BoomDialogState {
    data object None : BoomDialogState
    data object Import : BoomDialogState
    data object Export : BoomDialogState
}
