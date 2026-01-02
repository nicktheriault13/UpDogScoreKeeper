package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

object FourWayPlayScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FourWayPlayScreenModel() }
        val score by screenModel.score.collectAsState()
        val quads by screenModel.quads.collectAsState()
        val clickedZones by screenModel.clickedZones.collectAsState()
        val sweetSpotClicked by screenModel.sweetSpotClicked.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Score: $score", style = MaterialTheme.typography.h4)
                Text("Quads: $quads", style = MaterialTheme.typography.h4)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3x3 Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = if (fieldFlipped) (2 downTo 0) else (0..2)
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cols = if (fieldFlipped) (2 downTo 0) else (0..2)
                        cols.forEach { col ->
                            GridCell(row, col, clickedZones, sweetSpotClicked, screenModel)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.flipField() }) {
                    Text("Flip Field")
                }
                Button(onClick = { screenModel.reset() }) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
private fun GridCell(row: Int, col: Int, clickedZones: Set<Int>, sweetSpotClicked: Boolean, screenModel: FourWayPlayScreenModel) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        when {
            // Corner Zones
            row == 0 && col == 0 -> ZoneButton(1, clickedZones, screenModel::handleZoneClick)
            row == 0 && col == 2 -> ZoneButton(2, clickedZones, screenModel::handleZoneClick)
            row == 2 && col == 0 -> ZoneButton(4, clickedZones, screenModel::handleZoneClick)
            row == 2 && col == 2 -> ZoneButton(3, clickedZones, screenModel::handleZoneClick)
            // Sweet Spot
            row == 1 && col == 1 -> SweetSpotButton(sweetSpotClicked, screenModel::handleSweetSpotClick)
        }
    }
}

@Composable
private fun ZoneButton(zone: Int, clickedZones: Set<Int>, onClick: (Int) -> Unit) {
    val isClicked = zone in clickedZones
    Button(
        onClick = { onClick(zone) },
        enabled = !isClicked,
        colors = if (isClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Gray) else ButtonDefaults.buttonColors()
    ) {
        Text(zone.toString(), style = MaterialTheme.typography.h5)
    }
}

@Composable
private fun SweetSpotButton(isClicked: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (isClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors(backgroundColor = Color.Yellow)
    ) {
        Text("Sweet Spot")
    }
}
