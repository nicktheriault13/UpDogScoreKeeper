package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

@OptIn(ExperimentalFoundationApi::class)
object TimeWarpScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { TimeWarpScreenModel() }
        val score by screenModel.score.collectAsState()
        val timeRemaining by screenModel.timeRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpotClicked by screenModel.sweetSpotClicked.collectAsState()
        val allRollersClicked by screenModel.allRollersClicked.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()

        var showTimeDialog by remember { mutableStateOf(false) }

        if (showTimeDialog) {
            TimeInputDialog(screenModel) {
                showTimeDialog = false
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score: $score", style = MaterialTheme.typography.h5)
                Text("Time: %.2f".format(timeRemaining), style = MaterialTheme.typography.h5)

                // Custom button with long-press
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { if (isTimerRunning) screenModel.stopTimer() else screenModel.startTimer() },
                            onLongClick = { showTimeDialog = true }
                        )
                        .background(MaterialTheme.colors.primary, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isTimerRunning) "Stop" else "Start",
                        color = MaterialTheme.colors.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Misses and OB
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.incrementMisses() }) { Text("Misses: $misses") }
                Button(onClick = { screenModel.incrementOb() }) { Text("OB: $ob") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scoring Grid
            TimeWarpGrid(fieldFlipped, clickedZones, screenModel)

            Spacer(modifier = Modifier.height(24.dp))

            // Sweet Spot and All Rollers
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SweetSpotButton(sweetSpotClicked, clickedZones.size == 3, screenModel::handleSweetSpotClick)
                Button(
                    onClick = { screenModel.toggleAllRollers() },
                    colors = if (allRollersClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors()
                ) {
                    Text("All Rollers")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.flipField() }) { Text("Flip Field") }
                Button(onClick = { screenModel.reset() }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun TimeInputDialog(screenModel: TimeWarpScreenModel, onDismiss: () -> Unit) {
    var timeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Time Remaining") },
        text = {
            TextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                label = { Text("e.g., 12.34") }
            )
        },
        confirmButton = {
            Button(onClick = {
                screenModel.setTimeManually(timeInput)
                onDismiss()
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TimeWarpGrid(fieldFlipped: Boolean, clickedZones: Set<Int>, screenModel: TimeWarpScreenModel) {
    Column(
        modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)
    ) {
        val rows = 0..2
        val cols = if (fieldFlipped) 4 downTo 0 else 0..4

        rows.forEach { row ->
            Row(Modifier.height(if (row == 1) 80.dp else 40.dp)) { // Middle row is taller
                cols.forEach { col ->
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .border(0.5.dp, MaterialTheme.colors.onSurface)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when (col) {
                            0 -> ZoneButton(1, clickedZones, screenModel::handleZoneClick)
                            1 -> ZoneButton(2, clickedZones, screenModel::handleZoneClick)
                            2 -> if (row == 1) ZoneButton(3, clickedZones, screenModel::handleZoneClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneButton(zone: Int, clickedZones: Set<Int>, onClick: (Int) -> Unit) {
    val isClicked = zone in clickedZones
    Button(
        onClick = { onClick(zone) },
        enabled = !isClicked,
        colors = if (isClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Zone $zone")
    }
}

@Composable
private fun SweetSpotButton(isClicked: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = if (isClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors()
    ) {
        Text("Sweet Spot")
    }
}
