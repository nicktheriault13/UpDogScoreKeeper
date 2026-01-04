package com.ddsk.app.ui.screens.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.screens.games.FourWayPlayScreen
import com.ddsk.app.ui.screens.games.FrizgilityScreen
import com.ddsk.app.ui.screens.games.FunKeyScreen
import com.ddsk.app.ui.screens.games.GreedyScreen
import com.ddsk.app.ui.screens.games.FireballScreen
import com.ddsk.app.ui.screens.games.BoomScreen
import com.ddsk.app.ui.screens.games.SevenUpScreen
import com.ddsk.app.ui.screens.games.SpacedOutScreen
import com.ddsk.app.ui.screens.games.ThrowNGoScreen
import com.ddsk.app.ui.screens.games.TimeWarpScreen
import kotlinx.coroutines.flow.map

private enum class PlayerState { Stopped, Playing, Paused }

object HomeTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val title = "Home"
            val icon = rememberVectorPainter(Icons.Default.Home)

            return remember {
                TabOptions(
                    index = 0u,
                    title = title,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        Navigator(UpDogGamesScreen())
    }
}

private val updogGames = listOf(
    "Time Warp L1", "Throw N Go L1", "Spaced Out L1", "4 Way Play", "Far Out",
    "Greedy", "Boom!", "Fireball", "Funkey L1", "7 Up", "Frizgility L1"
)

private class UpDogGamesScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(updogGames) { gameName ->
                Button(
                    onClick = {
                        when (gameName) {
                            "Time Warp L1" -> navigator.push(TimeWarpScreen)
                            "Throw N Go L1" -> navigator.push(ThrowNGoScreen)
                            "4 Way Play" -> navigator.push(FourWayPlayScreen)
                            "Spaced Out L1" -> navigator.push(SpacedOutScreen)
                            "7 Up" -> navigator.push(SevenUpScreen)
                            "Funkey L1" -> navigator.push(FunKeyScreen)
                            "Frizgility L1" -> navigator.push(FrizgilityScreen)
                            "Greedy" -> navigator.push(GreedyScreen)
                            "Fireball" -> navigator.push(FireballScreen)
                            "Boom!" -> navigator.push(BoomScreen)
                            else -> navigator.push(GameScreen(gameName))
                        }
                    },
                    modifier = Modifier.height(80.dp)
                ) {
                    Text(gameName, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private data class GameScreen(val name: String) : Screen {
    @Composable
    override fun Content() {
        val audioPlayer = rememberAudioPlayer("assets/audio/sixty_seconds_timer.mp3")
        var playerState by remember { mutableStateOf(PlayerState.Stopped) }
        val currentTime by audioPlayer.currentTime.collectAsState()

        val remainingTime = remember(audioPlayer.duration, currentTime) {
            (audioPlayer.duration - currentTime) / 1000
        }

        // This effect resets the UI to "Stopped" when the audio finishes playing naturally.
        LaunchedEffect(audioPlayer.isPlaying) {
            if (!audioPlayer.isPlaying && playerState == PlayerState.Playing) {
                playerState = PlayerState.Stopped
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Scorekeeper for", style = MaterialTheme.typography.h6)
            Text(name, style = MaterialTheme.typography.h4)
            Spacer(Modifier.height(16.dp))

            if (playerState == PlayerState.Stopped) {
                Button(onClick = {
                    audioPlayer.play()
                    playerState = PlayerState.Playing
                }) {
                    Text("Timer")
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Time Remaining: $remainingTime s", style = MaterialTheme.typography.h5)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Pause/Resume Button
                        Button(
                            onClick = {
                                if (playerState == PlayerState.Playing) {
                                    audioPlayer.pause()
                                    playerState = PlayerState.Paused
                                } else {
                                    audioPlayer.play()
                                    playerState = PlayerState.Playing
                                }
                            },
                        ) {
                            Text(if (playerState == PlayerState.Playing) "Pause" else "Resume")
                        }

                        // Stop Button
                        Button(
                            onClick = {
                                audioPlayer.stop()
                                playerState = PlayerState.Stopped
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5x3 Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { // 3 rows
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) { // 5 columns
                            Card(
                                modifier = Modifier.height(50.dp).width(50.dp),
                                elevation = 2.dp
                            ) {
                                // Placeholder for grid cell content
                            }
                        }
                    }
                }
            }
        }
    }
}
