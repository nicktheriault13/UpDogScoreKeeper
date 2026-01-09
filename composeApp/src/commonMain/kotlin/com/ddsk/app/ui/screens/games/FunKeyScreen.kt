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
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

object FunKeyScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FunKeyScreenModel() }
        val score by screenModel.score.collectAsState()

        var isTimerRunning by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Fun Key") })

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) {
                audioPlayer.play()
            } else {
                audioPlayer.stop()
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add Timer Button
            Button(onClick = { isTimerRunning = !isTimerRunning }) {
                Text(if (isTimerRunning) "Stop Timer" else "Start Timer")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add Import functionality
            val filePicker = rememberFilePicker { result ->
                scope.launch {
                    when (result) {
                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                        else -> {}
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { filePicker.launch() }) { Text("Import") }
                Button(onClick = { /* Export placeholder */ }) { Text("Export") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Score: $score", style = MaterialTheme.typography.h4)

            Spacer(modifier = Modifier.height(32.dp))

            FunKeyGrid(screenModel)

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { screenModel.incrementMisses() }) { Text("Miss") }
                Button(onClick = { screenModel.toggleSweetSpot() }) { Text("Sweet Spot") }
                Button(onClick = { screenModel.reset() }) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun FunKeyGrid(screenModel: FunKeyScreenModel) {
    val isPurpleEnabled by screenModel.isPurpleEnabled.collectAsState()
    val isBlueEnabled by screenModel.isBlueEnabled.collectAsState()

    // Button counters
    val jump1Count by screenModel.jump1Count.collectAsState()
    val jump2Count by screenModel.jump2Count.collectAsState()
    val jump3Count by screenModel.jump3Count.collectAsState()
    val jump2bCount by screenModel.jump2bCount.collectAsState()
    val jump3bCount by screenModel.jump3bCount.collectAsState()
    val tunnelCount by screenModel.tunnelCount.collectAsState()
    val key1Count by screenModel.key1Count.collectAsState()
    val key2Count by screenModel.key2Count.collectAsState()
    val key3Count by screenModel.key3Count.collectAsState()
    val key4Count by screenModel.key4Count.collectAsState()

    Column(modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface)) {
        // Row 0
        Row(Modifier.height(60.dp)) {
            JumpButton("Jump3", 3, jump3Count, isPurpleEnabled, screenModel)
            KeyButton("Key1", 1, key1Count, isBlueEnabled, screenModel)
            JumpButton("Jump1", 1, jump1Count, isPurpleEnabled, screenModel)
            KeyButton("Key2", 2, key2Count, isBlueEnabled, screenModel)
            JumpButton("Jump3B", 3, jump3bCount, isPurpleEnabled, screenModel)
        }
        // Row 1 (Middle)
        Row(Modifier.height(100.dp)) {
            Spacer(Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface))
            Spacer(Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface))
            Button(onClick = { screenModel.toggleSweetSpot() }) { Text("Sweet Spot") }
            Spacer(Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface))
            Spacer(Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface))
        }
        // Row 2
        Row(Modifier.height(60.dp)) {
            JumpButton("Jump2", 2, jump2Count, isPurpleEnabled, screenModel)
            KeyButton("Key4", 4, key4Count, isBlueEnabled, screenModel)
            JumpButton("TUNNEL", 0, tunnelCount, isPurpleEnabled, screenModel) // Assuming tunnel is a jump with 0 points
            KeyButton("Key3", 3, key3Count, isBlueEnabled, screenModel)
            JumpButton("Jump2B", 2, jump2bCount, isPurpleEnabled, screenModel)
        }
    }
}

@Composable
fun RowScope.JumpButton(zoneId: String, points: Int, count: Int, isEnabled: Boolean, screenModel: FunKeyScreenModel) {
    Box(modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface)) {
        Button(
            onClick = { screenModel.handleCatch(FunKeyZoneType.JUMP, points, zoneId) },
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6750A4)), // Purple
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(zoneId.replace("B", ""), color = Color.White)
                Text(count.toString(), color = Color.White)
            }
        }
    }
}

@Composable
fun RowScope.KeyButton(zoneId: String, points: Int, count: Int, isEnabled: Boolean, screenModel: FunKeyScreenModel) {
    Box(modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colors.onSurface)) {
        Button(
            onClick = { screenModel.handleCatch(FunKeyZoneType.KEY, points, zoneId) },
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2979FF)), // Blue
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(points.toString(), color = Color.White, style = MaterialTheme.typography.h5)
                Text("Key", color = Color.White)
            }
        }
    }
}
