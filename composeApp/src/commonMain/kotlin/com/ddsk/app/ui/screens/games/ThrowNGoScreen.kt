package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

object ThrowNGoScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { ThrowNGoScreenModel() }
        val score by screenModel.score.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val ob by screenModel.ob.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Score Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score: $score", style = MaterialTheme.typography.h5)
                Button(onClick = { screenModel.incrementMisses() }) {
                    Text("Misses: $misses")
                }
                Button(onClick = { screenModel.incrementOb() }) {
                    Text("OB: $ob")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Scoring Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridButton(points = 1, onCatch = screenModel::handleCatch)
                    GridButton(points = 3, onCatch = screenModel::handleCatch)
                    GridButton(points = 4, onCatch = screenModel::handleCatch)
                    GridButton(points = 5, onCatch = screenModel::handleCatch)
                    GridButton(points = 6, onCatch = screenModel::handleCatch)
                }
                // Row 2 (Middle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridButton(points = 1, onCatch = screenModel::handleCatch)
                    GridButton(points = 6, onCatch = screenModel::handleCatch)
                    GridButton(points = 8, onCatch = screenModel::handleCatch)
                    GridButton(points = 10, onCatch = screenModel::handleCatch)
                    GridButton(points = 12, onCatch = screenModel::handleCatch)
                }
                // Row 3
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridButton(points = 1, onCatch = screenModel::handleCatch)
                    GridButton(points = 3, onCatch = screenModel::handleCatch)
                    GridButton(points = 4, onCatch = screenModel::handleCatch)
                    GridButton(points = 5, onCatch = screenModel::handleCatch)
                    GridButton(points = 6, onCatch = screenModel::handleCatch)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { screenModel.reset() }) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun GridButton(points: Int, onCatch: (Int) -> Unit) {
    Button(onClick = { onCatch(points) }) {
        Text(points.toString())
    }
}
