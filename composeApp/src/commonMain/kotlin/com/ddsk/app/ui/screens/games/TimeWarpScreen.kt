package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import com.ddsk.app.ui.theme.Palette
import kotlin.math.roundToInt

object TimeWarpScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { TimeWarpScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        // State access
        val score by screenModel.score.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpotClicked by screenModel.sweetSpotClicked.collectAsState()
        val allRollersClicked by screenModel.allRollersClicked.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val timeRemaining by screenModel.timeRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantQueue by screenModel.participantQueue.collectAsState()
        val completedParticipants by screenModel.completedParticipants.collectAsState()

        var showAddParticipant by remember { mutableStateOf(false) }
        var showTeams by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var showTimeInput by remember { mutableStateOf(false) }

        val audio = rememberAudioPlayer(getTimerAssetForGame("Time Warp"))

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audio.play() else audio.stop()
        }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left: Controls & Field
                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScoreHeader(
                            score = score,
                            timer = timeRemaining,
                            isTimerRunning = isTimerRunning,
                            activeParticipant = activeParticipant,
                            onTimerClick = { if (isTimerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            onShowTeams = { showTeams = true },
                            onLongPressTimer = { showTimeInput = true }
                        )

                        FieldGrid(
                            clickedZones = clickedZones,
                            fieldFlipped = fieldFlipped,
                            onZoneClick = { screenModel.handleZoneClick(it) }
                        )

                        BottomControls(
                            onPrevious = { /* screenModel.previousParticipant() */ },
                            onNext = { /* screenModel.nextParticipant() */ },
                            onSkip = { /* screenModel.skipParticipant() */ },
                            onShowTeams = { showTeams = true },
                            onAllRollers = { screenModel.toggleAllRollers() },
                            allRollersActive = allRollersClicked
                        )
                    }

                    // Right: Actions & Stats
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatsCard(misses = misses, ob = ob, onMiss = { screenModel.incrementMisses() }, onOb = { screenModel.incrementOb() })

                        ActionButtons(
                            sweetSpotActive = sweetSpotClicked,
                            onSweetSpot = { screenModel.handleSweetSpotClick() },
                            onFlipField = { screenModel.flipField() },
                            onReset = { screenModel.reset() },
                            onAddTeam = { showAddParticipant = true },
                            onHelp = { showHelp = true }
                        )
                    }
                }

                IconButton(
                    onClick = { navigator.pop() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Home, "Home", tint = Color.Gray)
                }
            }
        }

        if (showTeams) {
            TeamsDialog(
                active = activeParticipant,
                queue = participantQueue,
                completed = completedParticipants,
                onDismiss = { showTeams = false }
            )
        }
    }
}

@Composable
private fun ScoreHeader(
    score: Int,
    timer: Float,
    isTimerRunning: Boolean,
    activeParticipant: TimeWarpParticipant?,
    onTimerClick: () -> Unit,
    onShowTeams: () -> Unit,
    onLongPressTimer: () -> Unit
) {
    Card(elevation = 6.dp, backgroundColor = Palette.surface, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable { onShowTeams() }) {
                    Text("Current Team", color = Palette.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = activeParticipant?.displayName ?: "No Team Loaded",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }

                TimerButton(
                    timer = timer,
                    isRunning = isTimerRunning,
                    onClick = onTimerClick,
                    onLongPress = onLongPressTimer
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreBox(label = "Score", value = score.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimerButton(
    timer: Float,
    isRunning: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val color = if (isRunning) Palette.error else Palette.primary
    val label = if (isRunning) "Stop" else "Start"

    Card(
        backgroundColor = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(120.dp)
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(timer.formatTime(), color = Color.White)
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = Palette.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
    }
}

@Composable
private fun FieldGrid(
    clickedZones: Set<Int>,
    fieldFlipped: Boolean,
    onZoneClick: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("Field Grid Placeholder")
        }
    }
}

@Composable
private fun BottomControls(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onShowTeams: () -> Unit,
    onAllRollers: () -> Unit,
    allRollersActive: Boolean
) {
    Button(onClick = onAllRollers) { Text("All Rollers") }
}

@Composable
private fun StatsCard(
    misses: Int,
    ob: Int,
    onMiss: () -> Unit,
    onOb: () -> Unit
) {
    Column {
        Text("Misses: $misses")
        Button(onClick = onMiss) { Text("Miss +") }
        Text("OB: $ob")
        Button(onClick = onOb) { Text("OB +") }
    }
}

@Composable
private fun ActionButtons(
    sweetSpotActive: Boolean,
    onSweetSpot: () -> Unit,
    onFlipField: () -> Unit,
    onReset: () -> Unit,
    onAddTeam: () -> Unit,
    onHelp: () -> Unit
) {
    Column {
        Button(onClick = onSweetSpot) { Text(if (sweetSpotActive) "Sweet Spot (On)" else "Sweet Spot") }
        Button(onClick = onFlipField) { Text("Flip Field") }
        Button(onClick = onReset) { Text("Reset") }
        Button(onClick = onAddTeam) { Text("Add Team") }
        Button(onClick = onHelp) { Text("Help") }
    }
}

@Composable
private fun TeamsDialog(
    active: TimeWarpParticipant?,
    queue: List<TimeWarpParticipant>,
    completed: List<TimeWarpParticipant>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
        title = { Text("Teams") },
        text = {
            LazyColumn {
                item { Text("Active: ${active?.displayName ?: "None"}") }
                item { Text("Queue: ${queue.size}") }
                itemsIndexed(queue) { idx, item ->
                    Text("${idx + 1}. ${item.displayName}")
                }
                item { Text("Completed: ${completed.size}") }
                itemsIndexed(completed) { _, item ->
                    Text("${item.displayName} - ${item.result?.score}")
                }
            }
        }
    )
}

private fun Float.formatTime(): String {
    val totalSeconds = this.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

