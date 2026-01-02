package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

object FrizgilityScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FrizgilityScreenModel() }
        val score by screenModel.score.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Score: $score", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(32.dp))

            FrizgilityGrid(screenModel)

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.toggleSweetSpot() }) { Text("Sweet Spot") }
                Button(onClick = { screenModel.reset() }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun FrizgilityGrid(screenModel: FrizgilityScreenModel) {
    Row(modifier = Modifier.fillMaxWidth(0.95f).height(240.dp).border(1.dp, Color.Black)) {
        // Left Column for Obstacles/Fails
        Column(modifier = Modifier.weight(3f)) {
            Row(modifier = Modifier.weight(2f)) {
                ObstacleButton(1, screenModel)
                ObstacleButton(2, screenModel)
                ObstacleButton(3, screenModel)
            }
            Row(modifier = Modifier.weight(1f)) {
                FailButton(1, screenModel)
                FailButton(2, screenModel)
                FailButton(3, screenModel)
            }
        }

        // Right Column for Catches/Misses
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CatchButton("3-10", 3, screenModel)
            CatchButton("10+", 10, screenModel)
            MissButton(screenModel)
        }
    }
}

@Composable
private fun RowScope.ObstacleButton(num: Int, screenModel: FrizgilityScreenModel) {
    val obstacleCount by when(num) {
        1 -> screenModel.obstacle1Count.collectAsState()
        2 -> screenModel.obstacle2Count.collectAsState()
        else -> screenModel.obstacle3Count.collectAsState()
    }
    val obstaclePhaseActive by screenModel.obstaclePhaseActive.collectAsState()
    val obstacleOrFailClicked by screenModel.obstacleOrFailClicked.collectAsState()

    Button(
        onClick = { screenModel.handleObstacleClick(num) },
        enabled = obstaclePhaseActive && num !in obstacleOrFailClicked,
        modifier = Modifier.weight(1f).fillMaxSize().border(1.dp, Color.Black)
    ) {
        Text("Obstacle $num\n($obstacleCount)", textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.FailButton(num: Int, screenModel: FrizgilityScreenModel) {
    val failCount by when(num) {
        1 -> screenModel.fail1Count.collectAsState()
        2 -> screenModel.fail2Count.collectAsState()
        else -> screenModel.fail3Count.collectAsState()
    }
    val obstaclePhaseActive by screenModel.obstaclePhaseActive.collectAsState()
    val obstacleOrFailClicked by screenModel.obstacleOrFailClicked.collectAsState()

    Button(
        onClick = { screenModel.handleFailClick(num) },
        enabled = obstaclePhaseActive && num !in obstacleOrFailClicked,
        modifier = Modifier.weight(1f).fillMaxSize().border(1.dp, Color.Black)
    ) {
        Text("Fail $num\n($failCount)", textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.CatchButton(label: String, points: Int, screenModel: FrizgilityScreenModel) {
    val catchCount by if (points == 3) screenModel.catch3to10Count.collectAsState() else screenModel.catch10plusCount.collectAsState()
    val catchPhaseActive by screenModel.catchPhaseActive.collectAsState()
    val catchClicked by screenModel.catchClicked.collectAsState()

    Button(
        onClick = { screenModel.handleCatchClick(points) },
        enabled = catchPhaseActive && points !in catchClicked,
        modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Black)
    ) {
        Text("$label\n($catchCount)", textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.MissButton(screenModel: FrizgilityScreenModel) {
    val missCount by screenModel.missCount.collectAsState()
    val catchPhaseActive by screenModel.catchPhaseActive.collectAsState()
    val missClicksInPhase by screenModel.missClicksInPhase.collectAsState()

    Button(
        onClick = { screenModel.handleMissClick() },
        enabled = catchPhaseActive && missClicksInPhase < 3,
        modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Black)
    ) {
        Text("Miss\n($missCount)", textAlign = TextAlign.Center)
    }
}
