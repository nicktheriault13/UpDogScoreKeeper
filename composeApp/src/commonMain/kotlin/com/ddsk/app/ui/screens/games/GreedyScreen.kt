package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel

object GreedyScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = getScreenModel<GreedyScreenModel>()
        val score by screenModel.score.collectAsState()
        val throwZone by screenModel.throwZone.collectAsState()
        val misses by screenModel.misses.collectAsState()

        var showBonusDialog by remember { mutableStateOf(false) }

        if (showBonusDialog) {
            SweetSpotBonusDialog(screenModel) { showBonusDialog = false }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Score: $score", style = MaterialTheme.typography.h4)
            Text("Throw Zone: $throwZone", style = MaterialTheme.typography.h5)
            Text("Misses: $misses", style = MaterialTheme.typography.h6)

            Spacer(modifier = Modifier.height(24.dp))

            GreedyGrid(screenModel)

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.nextThrowZone(clockwise = true) }) { Text("Rotate CW") }
                Button(onClick = { screenModel.nextThrowZone(clockwise = false) }) { Text("Rotate CCW") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.incrementMisses() }) { Text("Miss") }
                Button(onClick = { showBonusDialog = true }) { Text("Sweet Spot Bonus") }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { screenModel.reset() }) { Text("Reset") }
        }
    }
}

@Composable
private fun SweetSpotBonusDialog(screenModel: GreedyScreenModel, onDismiss: () -> Unit) {
    var bonusInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Sweet Spot Bonus (1-8)") },
        text = { TextField(value = bonusInput, onValueChange = { bonusInput = it }) },
        confirmButton = {
            Button(onClick = {
                screenModel.setSweetSpotBonus(bonusInput.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Set") }
        }
    )
}

@Composable
private fun GreedyGrid(screenModel: GreedyScreenModel) {
    val rotationDegrees by screenModel.rotationDegrees.collectAsState()
    val activeButtons by screenModel.activeButtonsByZone.collectAsState()
    val throwZone by screenModel.throwZone.collectAsState()

    Box(modifier = Modifier.rotate(rotationDegrees.toFloat())) {
        Column(modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)) {
            (0..2).forEach { row ->
                Row(modifier = Modifier.height(80.dp)) {
                    (0..2).forEach { col ->
                        val buttonLabel = getButtonLabel(row, col)
                        val isEnabled = buttonLabel != null && buttonLabel !in activeButtons[throwZone]
                        Box(modifier = Modifier.size(80.dp).border(0.5.dp, MaterialTheme.colors.onSurface)) {
                            if (buttonLabel != null) {
                                Button(
                                    onClick = { screenModel.handleButtonPress(buttonLabel) },
                                    enabled = isEnabled,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(buttonLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getButtonLabel(row: Int, col: Int): String? {
    return when {
        row == 0 && col == 0 -> "X"
        row == 0 && col == 1 -> "Y"
        row == 0 && col == 2 -> "Z"
        row == 1 && col == 1 -> "Sweet Spot"
        else -> null
    }
}
