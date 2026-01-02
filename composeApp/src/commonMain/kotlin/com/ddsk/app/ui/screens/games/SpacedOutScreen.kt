package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.border
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

object SpacedOutScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SpacedOutScreenModel() }
        val score by screenModel.score.collectAsState()
        val spacedOutCount by screenModel.spacedOutCount.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()
        val clickedZones by screenModel.clickedZonesInRound.collectAsState()
        val sweetSpotBonusOn by screenModel.sweetSpotBonusOn.collectAsState()
        val fieldFlipped by screenModel.fieldFlipped.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score: $score", style = MaterialTheme.typography.h5)
                Text("Spaced Out: $spacedOutCount", style = MaterialTheme.typography.h5)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.incrementMisses() }) { Text("Misses: $misses") }
                Button(onClick = { screenModel.incrementOb() }) { Text("OB: $ob") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scoring Grid
            SpacedOutGrid(fieldFlipped, clickedZones, screenModel)

            Spacer(modifier = Modifier.height(24.dp))

            // Sweet Spot Bonus
            Button(
                onClick = { screenModel.toggleSweetSpotBonus() },
                colors = if (sweetSpotBonusOn) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors()
            ) {
                Text("SweetSpot Bonus")
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
private fun SpacedOutGrid(fieldFlipped: Boolean, clickedZones: Set<SpacedOutZone>, screenModel: SpacedOutScreenModel) {
    Column(
        modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)
    ) {
        val rows = if (fieldFlipped) 2 downTo 0 else 0..2
        val cols = if (fieldFlipped) 4 downTo 0 else 0..4

        rows.forEach { row ->
            Row(Modifier.height(if (row == 1) 80.dp else 40.dp)) { // Middle row is taller
                cols.forEach { col ->
                    val currentZone = getZoneForCell(row, col)
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .border(0.5.dp, MaterialTheme.colors.onSurface)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentZone != null) {
                            ZoneButton(currentZone, clickedZones, screenModel::handleZoneClick)
                        }
                    }
                }
            }
        }
    }
}

// Maps a grid cell to a specific scoring zone based on the React Native layout
private fun getZoneForCell(row: Int, col: Int): SpacedOutZone? {
    return when {
        row == 2 && col == 1 -> SpacedOutZone.ZONE_1
        row == 0 && col == 2 -> SpacedOutZone.ZONE_2
        row == 0 && col == 3 -> SpacedOutZone.ZONE_3
        row == 1 && col == 2 -> SpacedOutZone.SWEET_SPOT_GRID
        else -> null
    }
}

@Composable
private fun ZoneButton(zone: SpacedOutZone, clickedZones: Set<SpacedOutZone>, onClick: (SpacedOutZone) -> Unit) {
    val isClicked = zone in clickedZones
    Button(
        onClick = { onClick(zone) },
        colors = if (isClicked) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxSize()
    ) {
        Text(zone.name.replace("_", " ").toLowerCase().capitalize())
    }
}
