package com.ddsk.app.ui.screens.games

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.screens.games.ui.GameHomeOverlay
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame

private val primaryBlue = Color(0xFF2979FF)
private val infoCyan = Color(0xFF00B8D4)
private val successGreen = Color(0xFF00C853)
private val warningOrange = Color(0xFFFF9100)
private val boomPink = Color(0xFFF500A1)
private val errorRed = Color(0xFFD50000)
private val disabledContent = Color(0xFF222222)

object FrizgilityScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FrizgilityScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val uiState by screenModel.uiState.collectAsState()
        val timerRunning by screenModel.timerRunning.collectAsState()
        val timeLeft by screenModel.timeLeft.collectAsState()
        val logEntries by screenModel.logEntries.collectAsState()

        // Audio
        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Frizgility") })
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
                    // LEFT COLUMN: Score & Controls
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        FrizgilityScoreCard(uiState)
                        Box(modifier = Modifier.weight(1f)) {
                            FrizgilityGrid(screenModel, uiState)
                        }
                        FrizgilityControlRow(screenModel)
                    }

                    // RIGHT COLUMN: Timer, Queue, Logs
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(columnSpacing)
                    ) {
                        FrizgilityTimerCard(
                            timerRunning = timerRunning,
                            timeLeft = timeLeft,
                            onStartStop = {
                                if (timerRunning) screenModel.stopTimer() else screenModel.startTimer()
                            },
                            onReset = screenModel::resetGame
                        )
                        FrizgilityQueueCard(uiState)
                        FrizgilityLogCard(logEntries, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun FrizgilityScoreCard(uiState: FrizgilityUiState) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Score: ${uiState.scoreBreakdown.totalScore}", style = MaterialTheme.typography.h4)
                if (uiState.sweetSpotActive) {
                    Text("Sweet Spot!", color = boomPink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val active = uiState.activeParticipant
            Text(
                text = active?.let { "${it.handler} & ${it.dog}" } ?: "No active team",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FrizgilityGrid(screenModel: FrizgilityScreenModel, uiState: FrizgilityUiState) {
    Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Text("Obstacles: ${uiState.counters.obstacle1 + uiState.counters.obstacle2 + uiState.counters.obstacle3}")
                Text("Catches: ${uiState.counters.catch3to10 + uiState.counters.catch10plus}")
                Text("Misses: ${uiState.counters.miss}")
            }

            // Obstacle Section
            Text("Agility Obstacles", style = MaterialTheme.typography.subtitle1,
                 color = if (uiState.obstaclePhaseActive) primaryBlue else disabledContent)
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { lane ->
                    val locked = lane in uiState.laneLocks
                    Column(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { screenModel.handleObstacleClick(lane) },
                            enabled = uiState.obstaclePhaseActive && !locked,
                            modifier = Modifier.weight(2f).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (locked) successGreen else primaryBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Obs $lane")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { screenModel.handleFailClick(lane) },
                            enabled = uiState.obstaclePhaseActive && !locked,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = errorRed)
                        ) {
                            Text("Fail")
                        }
                    }
                }
            }

            // Catch Section
            Text("Catches", style = MaterialTheme.typography.subtitle1,
                 color = if (uiState.catchPhaseActive) primaryBlue else disabledContent)
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val catch3Locked = 3 in uiState.catchLocks
                Button(
                    onClick = { screenModel.handleCatchClick(3) },
                    enabled = uiState.catchPhaseActive && !catch3Locked,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (catch3Locked) successGreen else infoCyan,
                         contentColor = Color.White
                    )
                ) {
                    Text("Zone 1 (3)")
                }

                val catch10Locked = 10 in uiState.catchLocks
                Button(
                    onClick = { screenModel.handleCatchClick(10) },
                    enabled = uiState.catchPhaseActive && !catch10Locked,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (catch10Locked) successGreen else boomPink,
                        contentColor = Color.White
                    )
                ) {
                    Text("Zone 2 (10)")
                }

                Button(
                    onClick = { screenModel.handleMissClick() },
                    enabled = true, // Miss always enabled? Or only in phases?
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = disabledContent)
                ) {
                    Text("Miss (${uiState.missClicksInPhase})")
                }
            }
        }
    }
}

@Composable
fun FrizgilityControlRow(screenModel: FrizgilityScreenModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { screenModel.undo() }, colors = ButtonDefaults.buttonColors(backgroundColor = warningOrange)) {
            Text("Undo")
        }
        Button(onClick = { screenModel.toggleSweetSpot() }) {
            Text("Sweet Spot")
        }
        Button(onClick = { screenModel.toggleAllRollers() }) {
            Text("All Rollers")
        }
    }
}

@Composable
fun FrizgilityTimerCard(
    timerRunning: Boolean,
    timeLeft: Int,
    onStartStop: () -> Unit,
    onReset: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Time Remaining", style = MaterialTheme.typography.caption)
                Text("${timeLeft}s", style = MaterialTheme.typography.h3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartStop,
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (timerRunning) errorRed else successGreen)
                ) {
                    Text(if (timerRunning) "Stop" else "Start")
                }
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun FrizgilityQueueCard(uiState: FrizgilityUiState) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            item { Text("Queue (${uiState.queue.size})", fontWeight = FontWeight.Bold) }
            items(uiState.queue) { p ->
                Text("${p.handler} - ${p.dog}", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun FrizgilityLogCard(logs: List<String>, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
         LazyColumn(modifier = Modifier.padding(8.dp)) {
            item { Text("Log", fontWeight = FontWeight.Bold) }
            items(logs) { log ->
                Text(log, style = MaterialTheme.typography.caption, modifier = Modifier.padding(2.dp))
            }
        }
    }
}

