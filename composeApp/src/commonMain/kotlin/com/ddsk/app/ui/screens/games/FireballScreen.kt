package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel

data class Point(val row: Int, val col: Int)

object FireballScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = getScreenModel<FireballScreenModel>()
        val totalScore by screenModel.totalScore.collectAsState()
        val currentBoardScore by screenModel.currentBoardScore.collectAsState()
        val isFieldFlipped by screenModel.isFieldFlipped.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Total Score: $totalScore", style = MaterialTheme.typography.h4)
            Text("Current Board: $currentBoardScore", style = MaterialTheme.typography.h5)

            Spacer(modifier = Modifier.height(32.dp))

            FireballGrid(screenModel, isFieldFlipped)

            Spacer(modifier = Modifier.weight(1f))

            val isFireballActive by screenModel.isFireballActive.collectAsState()
            val sweetSpotBonusAwarded by screenModel.sweetSpotBonusAwarded.collectAsState()

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { screenModel.toggleFireball() },
                    colors = if (isFireballActive) ButtonDefaults.buttonColors(backgroundColor = Color.Red) else ButtonDefaults.buttonColors()
                ) { Text("Fireball Mode") }
                
                Button(
                    onClick = { screenModel.toggleManualSweetSpot() },
                    colors = if (sweetSpotBonusAwarded) ButtonDefaults.buttonColors(backgroundColor = Color.Green) else ButtonDefaults.buttonColors()
                ) { Text("Sweet Spot") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.clearBoard() }) { Text("Clear Board") }
                Button(onClick = { screenModel.resetGame() }) { Text("Reset Game") }
                Button(
                    onClick = { screenModel.toggleFieldOrientation() },
                    colors = if (isFieldFlipped) ButtonDefaults.buttonColors(backgroundColor = Color(0xFF455A64)) else ButtonDefaults.buttonColors()
                ) { Text("Flip Field") }
            }
        }
    }
}

@Composable
private fun FireballGrid(screenModel: FireballScreenModel, isFieldFlipped: Boolean) {
    val rowOrder = if (isFieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
    val colOrder = if (isFieldFlipped) listOf(2, 1, 0) else listOf(0, 1, 2)
    Column(modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)) {
        rowOrder.forEach { row ->
            Row(modifier = Modifier.height(60.dp)) {
                colOrder.forEach { col ->
                    val cellData = getCellData(row, col)
                    if (cellData != null) {
                        ScoringButton(cellData.label, cellData.row, cellData.col, screenModel)
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .border(0.5.dp, MaterialTheme.colors.onSurface)
                        )
                    }
                }
            }
        }
    }
}

private fun getCellData(row: Int, col: Int): CellData? {
    return when {
        row == 0 && col == 0 -> CellData("8", row, col)
        row == 0 && col == 1 -> CellData("7", row, col)
        row == 0 && col == 2 -> CellData("5", row, col)
        row == 1 && col == 0 -> CellData("6", row, col)
        row == 1 && col == 1 -> CellData("4", row, col)
        row == 1 && col == 2 -> CellData("2", row, col)
        row == 2 && col == 0 -> CellData("3", row, col)
        row == 2 && col == 1 -> CellData("1", row, col)
        else -> null
    }
}

private data class CellData(val label: String, val row: Int, val col: Int)

@Composable
private fun RowScope.ScoringButton(label: String, row: Int, col: Int, screenModel: FireballScreenModel) {
    val clickedZones by screenModel.clickedZones.collectAsState()
    val fireballZones by screenModel.fireballZones.collectAsState()
    val point = Point(row, col)

    val isClicked = point in clickedZones
    val isFireball = point in fireballZones

    val color = when {
        isFireball -> Color.Red
        isClicked -> Color.Green
        else -> MaterialTheme.colors.primary
    }

    Button(
        onClick = { screenModel.handleZoneClick(row, col) },
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        modifier = Modifier.weight(1f).fillMaxSize().border(0.5.dp, MaterialTheme.colors.onSurface)
    ) {
        Text(label)
    }
}
