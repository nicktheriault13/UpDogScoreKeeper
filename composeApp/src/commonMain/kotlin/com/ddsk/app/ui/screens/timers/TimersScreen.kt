package com.ddsk.app.ui.screens.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.ui.components.HomeButtonFrame
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.ui.theme.Palette
import kotlin.math.max

object TimersScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Surface(modifier = Modifier.fillMaxSize(), color = Palette.background) {
            HomeButtonFrame(navigator = navigator) {
                TimersContent()
            }
        }
    }
}

@Composable
private fun TimersContent() {
    var currentGame by remember { mutableStateOf<TimerGameDef?>(null) }
    var currentAsset by remember { mutableStateOf(DEFAULT_TIMER_ASSET) }
    var playerState by remember { mutableStateOf(TimerPlayerState.Stopped) }
    var previousTime by remember { mutableIntStateOf(0) }
    var playToken by remember { mutableIntStateOf(0) }

    val audioPlayer = rememberAudioPlayer(currentAsset)
    val currentTime by audioPlayer.currentTime.collectAsState()
    val duration = audioPlayer.duration

    LaunchedEffect(currentAsset, playToken) {
        if (playerState == TimerPlayerState.Playing) {
            audioPlayer.play()
        }
    }

    LaunchedEffect(currentTime) {
        if (playerState == TimerPlayerState.Playing && previousTime > 0 && currentTime == 0) {
            playerState = TimerPlayerState.Stopped
            currentGame = null
        }
        previousTime = currentTime
    }

    fun handleTimerTap(game: TimerGameDef) {
        if (playerState == TimerPlayerState.Playing && currentGame?.name == game.name) {
            audioPlayer.stop()
            playerState = TimerPlayerState.Stopped
            currentGame = null
            previousTime = 0
            return
        }

        val nextAsset = game.asset.resolve()
        audioPlayer.stop()
        currentAsset = nextAsset
        currentGame = game
        playerState = TimerPlayerState.Playing
        previousTime = 0
        playToken++
    }

    fun handlePauseResume() {
        when (playerState) {
            TimerPlayerState.Playing -> {
                audioPlayer.pause()
                playerState = TimerPlayerState.Paused
            }
            TimerPlayerState.Paused -> {
                audioPlayer.play()
                playerState = TimerPlayerState.Playing
            }
            TimerPlayerState.Stopped -> Unit
        }
    }

    fun handleStop() {
        audioPlayer.stop()
        playerState = TimerPlayerState.Stopped
        currentGame = null
        previousTime = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPhone = maxWidth < 600.dp
        val columns = if (isPhone) 2 else 4
        val buttonHeight = if (isPhone) 72.dp else 88.dp
        val lazyGridState = rememberLazyGridState()

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            // Home button frame already provides base padding.
            contentPadding = PaddingValues(horizontal = if (isPhone) 12.dp else 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TimersHeader(isPhone = isPhone)
            }

            if (playerState != TimerPlayerState.Stopped && currentGame != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NowPlayingCard(
                        gameName = currentGame?.name.orEmpty(),
                        currentTimeMillis = currentTime,
                        durationMillis = duration,
                        playerState = playerState,
                        onPauseResume = ::handlePauseResume,
                        onStop = ::handleStop,
                        isPhone = isPhone
                    )
                }
            }

            items(SharedTimerGames, key = { it.name }) { game ->
                TimerGridButton(
                    game = game,
                    isActive = currentGame?.name == game.name && playerState != TimerPlayerState.Stopped,
                    onClick = { handleTimerTap(game) },
                    height = buttonHeight
                )
            }
        }
    }
}

@Composable
private fun TimersHeader(isPhone: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(if (isPhone) 12.dp else 16.dp))
        Text(
            text = "Game Timers",
            style = if (isPhone) MaterialTheme.typography.h5 else MaterialTheme.typography.h4,
            color = Palette.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap a button to play that game's timer",
            style = MaterialTheme.typography.body2,
            color = Palette.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimerGridButton(game: TimerGameDef, isActive: Boolean, onClick: () -> Unit, height: Dp) {
    val background = if (isActive) Palette.warning else Palette.primary
    val contentColor = if (isActive) Palette.onWarning else Palette.onPrimary

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height),
        colors = ButtonDefaults.buttonColors(backgroundColor = background, contentColor = contentColor)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(game.name, textAlign = TextAlign.Center)
            if (isActive) {
                Spacer(Modifier.height(2.dp))
                Text("Playing", style = MaterialTheme.typography.caption, color = contentColor)
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    gameName: String,
    currentTimeMillis: Int,
    durationMillis: Int,
    playerState: TimerPlayerState,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    isPhone: Boolean,
) {
    val progress = if (durationMillis > 0) currentTimeMillis / durationMillis.toFloat() else 0f
    val remainingMillis = (durationMillis - currentTimeMillis).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 6.dp,
        backgroundColor = Palette.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Playing: $gameName", color = Palette.onSurface, style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(formatTime(remainingMillis), color = Palette.onSurface, style = MaterialTheme.typography.h6)
                    Text("Time Remaining", color = Palette.onSurfaceVariant, style = MaterialTheme.typography.caption)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (durationMillis > 0) formatTime(durationMillis) else "--:--",
                        color = Palette.onSurfaceVariant,
                        style = MaterialTheme.typography.body2
                    )
                    Text("Total Duration", color = Palette.onSurfaceVariant, style = MaterialTheme.typography.caption)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(if (isPhone) 12.dp else 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPauseResume,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (playerState == TimerPlayerState.Paused) Palette.success else Palette.warning,
                        contentColor = if (playerState == TimerPlayerState.Paused) Palette.onSuccess else Palette.onWarning
                    )
                ) {
                    Text(if (playerState == TimerPlayerState.Paused) "Resume" else "Pause", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.error, contentColor = Palette.onError)
                ) {
                    Text("Stop", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val totalSeconds = max(milliseconds / 1000, 0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val secondsText = if (seconds < 10) "0$seconds" else seconds.toString()
    return "$minutes:$secondsText"
}

private enum class TimerPlayerState { Stopped, Playing, Paused }
