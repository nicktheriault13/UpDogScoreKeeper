package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.media.rememberAudioPlayer
import com.ddsk.app.persistence.*
import com.ddsk.app.ui.components.GameHomeButton
import com.ddsk.app.ui.screens.timers.getTimerAssetForGame
import kotlinx.coroutines.launch

// Shared Color Palettes
private val mdPrimary = Color(0xFF6750A4)
private val mdOnPrimary = Color(0xFFFFFFFF)
private val mdPrimaryContainer = Color(0xFFEADDFF)
private val mdOnPrimaryContainer = Color(0xFF21005D)
private val mdSecondary = Color(0xFF625B71)
private val mdOnSecondary = Color(0xFFFFFFFF)
private val mdSecondaryContainer = Color(0xFFE8DEF8)
private val mdOnSecondaryContainer = Color(0xFF1D192B)
private val mdTertiary = Color(0xFF7D5260)
private val mdOnTertiary = Color(0xFFFFFFFF)
private val mdTertiaryContainer = Color(0xFFFFD8E4)
private val mdOnTertiaryContainer = Color(0xFF31111D)
private val mdError = Color(0xFFB3261E)
private val mdOnError = Color(0xFFFFFFFF)
private val mdErrorContainer = Color(0xFFF9DEDC)
private val mdOnErrorContainer = Color(0xFF410E0B)
private val mdBackground = Color(0xFFFFFBFE)
private val mdOnBackground = Color(0xFF1C1B1F)
private val mdSurface = Color(0xFFFFFBFE)
private val mdOnSurface = Color(0xFF1C1B1F)
private val mdSurfaceVariant = Color(0xFFE7E0EC)
private val mdOnSurfaceVariant = Color(0xFF49454F)
private val mdSurfaceContainer = Color(0xFFF5EFF7)
private val mdOutline = Color(0xFF79747E)
private val mdOutlineVariant = Color(0xFFCAC4D0)
private val mdInverseSurface = Color(0xFF313033)
private val mdInverseOnSurface = Color(0xFFF4EFF4)
private val mdInversePrimary = Color(0xFFD0BCFF)
private val mdText = Color(0xFF1C1B1F)

private val vPrimary = Color(0xFF2979FF)
private val vPrimaryOn = Color(0xFFFFFFFF)
private val vSuccess = Color(0xFF00C853)
private val vSuccessOn = Color(0xFFFFFFFF)
private val vWarning = Color(0xFFFF9100)
private val vWarningOn = Color(0xFFFFFFFF)
private val vError = Color(0xFFD50000)
private val vErrorOn = Color(0xFFFFFFFF)
private val vInfo = Color(0xFF00B8D4)
private val vInfoOn = Color(0xFFFFFFFF)
private val vTertiary = Color(0xFFF500A1)
private val vTertiaryOn = Color(0xFFFFFFFF)

object FunKeyScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FunKeyScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }
        val score by screenModel.score.collectAsState()
        val misses by screenModel.misses.collectAsState()
        val sweetSpotOn by screenModel.sweetSpotOn.collectAsState()
        val isPurpleEnabled by screenModel.isPurpleEnabled.collectAsState()
        val isBlueEnabled by screenModel.isBlueEnabled.collectAsState()
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
        val activatedKeys by screenModel.activatedKeys.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantQueue by screenModel.participantQueue.collectAsState()

        var isTimerRunning by remember { mutableStateOf(false) }
        var showAddDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val audioPlayer = rememberAudioPlayer(remember { getTimerAssetForGame("Fun Key") })
        val filePicker = rememberFilePicker { result ->
            scope.launch {
                when (result) {
                    is ImportResult.Csv -> screenModel.importParticipantsFromCsv(result.contents)
                    is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(result.bytes)
                    else -> {}
                }
            }
        }
        val fileExporter = rememberFileExporter()

        LaunchedEffect(isTimerRunning) {
            if (isTimerRunning) audioPlayer.play() else audioPlayer.stop()
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Home button is rendered inside the top score card (see FunKeyTopBar) to avoid overlap.

                val isCompactHeight = maxHeight < 720.dp
                val contentSpacing = if (isCompactHeight) 12.dp else 16.dp
                val queueWeight = if (isCompactHeight) 0.28f else 0.24f
                val fieldWeight = if (isCompactHeight) 0.52f else 0.6f
                val actionWeight = 1f - queueWeight - fieldWeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing)
                ) {
                    FunKeyTopBar(
                        navigator = navigator,
                        isTimerRunning = isTimerRunning,
                        score = score,
                        misses = misses,
                        activeParticipant = activeParticipant,
                        onTimerToggle = { isTimerRunning = !isTimerRunning },
                        onAddTeam = { showAddDialog = true }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        FunKeySidebar(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .fillMaxHeight(),
                            onImport = { filePicker.launch() },
                            onExport = {
                                exportParticipants(fileExporter, activeParticipant, participantQueue)
                            },
                            onAddTeam = { showAddDialog = true },
                            onNext = screenModel::nextParticipant,
                            onSkip = screenModel::skipParticipant,
                            onResetScore = screenModel::reset,
                            onClearState = { screenModel.reset() }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(contentSpacing)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(queueWeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                ParticipantQueueCard(
                                    activeParticipant = activeParticipant,
                                    participantQueue = participantQueue,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(fieldWeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                FunKeyFieldCard(
                                    isPurpleEnabled = isPurpleEnabled,
                                    isBlueEnabled = isBlueEnabled,
                                    sweetSpotOn = sweetSpotOn,
                                    jump3Count = jump3Count,
                                    jump1Count = jump1Count,
                                    jump3bCount = jump3bCount,
                                    jump2Count = jump2Count,
                                    jump2bCount = jump2bCount,
                                    tunnelCount = tunnelCount,
                                    key1Count = key1Count,
                                    key2Count = key2Count,
                                    key3Count = key3Count,
                                    key4Count = key4Count,
                                    activatedKeys = activatedKeys,
                                    onJump = { _, zone -> screenModel.handleCatch(FunKeyZoneType.JUMP, 0, zone) },
                                    onKey = { _, zone -> screenModel.handleCatch(FunKeyZoneType.KEY, 0, zone) },
                                    onSweetSpot = screenModel::toggleSweetSpot,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(actionWeight)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                FunKeyActionRow(
                                    sweetSpotOn = sweetSpotOn,
                                    onMiss = screenModel::incrementMisses,
                                    onSweetSpot = screenModel::toggleSweetSpot,
                                    onReset = screenModel::reset,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            FunKeyAddParticipantDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { handler, dog, utn ->
                    screenModel.addParticipant(handler, dog, utn)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun FunKeyTopBar(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    isTimerRunning: Boolean,
    score: Int,
    misses: Int,
    activeParticipant: FunKeyParticipant?,
    onTimerToggle: () -> Unit,
    onAddTeam: () -> Unit
) {
    Card(elevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameHomeButton(
                navigator = navigator,
                modifier = Modifier
                    .height(40.dp)
            )

            Button(
                onClick = onTimerToggle,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isTimerRunning) vWarning else vPrimary,
                    contentColor = if (isTimerRunning) vWarningOn else vPrimaryOn
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Text(if (isTimerRunning) "Stop Timer" else "Start Timer", fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Current Team", fontSize = 11.sp, color = mdOutline)
                Text(
                    activeParticipant?.let { "${it.handler} & ${it.dog}" } ?: "No team loaded",
                    fontWeight = FontWeight.Bold,
                    color = mdText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Score", fontSize = 11.sp, color = mdOutline)
                Text("$score", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = mdText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Misses", fontSize = 11.sp, color = mdOutline)
                Text("$misses", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = mdText)
            }
            Button(
                onClick = onAddTeam,
                colors = ButtonDefaults.buttonColors(backgroundColor = mdPrimaryContainer, contentColor = mdPrimary),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Add Team", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FunKeyFieldCard(
    isPurpleEnabled: Boolean,
    isBlueEnabled: Boolean,
    sweetSpotOn: Boolean,
    jump3Count: Int,
    jump1Count: Int,
    jump3bCount: Int,
    jump2Count: Int,
    jump2bCount: Int,
    tunnelCount: Int,
    key1Count: Int,
    key2Count: Int,
    key3Count: Int,
    key4Count: Int,
    activatedKeys: Set<String>,
    onJump: (Int, String) -> Unit,
    onKey: (Int, String) -> Unit,
    onSweetSpot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(elevation = 8.dp, modifier = modifier) {
        BoxWithConstraints {
            val spacing = 12.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(mdSurfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                FieldRow(modifier = Modifier.weight(1f)) {
                    JumpCell("Jump 3", jump3Count, isPurpleEnabled) { onJump(3, "JUMP3") }
                    KeyCell("Key 1", 1, key1Count, isBlueEnabled, activatedKeys.contains("KEY1")) { onKey(1, "KEY1") }
                    JumpCell("Jump 1", jump1Count, isPurpleEnabled) { onJump(1, "JUMP1") }
                    KeyCell("Key 2", 2, key2Count, isBlueEnabled, activatedKeys.contains("KEY2")) { onKey(2, "KEY2") }
                    JumpCell("Jump 3", jump3bCount, isPurpleEnabled) { onJump(3, "JUMP3B") }
                }
                FieldRow(modifier = Modifier.weight(0.6f)) {
                    SpacerCell()
                    SpacerCell()
                    SweetSpotCell(sweetSpotOn, onSweetSpot)
                    SpacerCell()
                    SpacerCell()
                }
                FieldRow(modifier = Modifier.weight(1f)) {
                    JumpCell("Jump 2", jump2Count, isPurpleEnabled) { onJump(2, "JUMP2") }
                    KeyCell("Key 4", 4, key4Count, isBlueEnabled, activatedKeys.contains("KEY4")) { onKey(4, "KEY4") }
                    JumpCell("Tunnel", tunnelCount, isPurpleEnabled) { onJump(0, "TUNNEL") }
                    KeyCell("Key 3", 3, key3Count, isBlueEnabled, activatedKeys.contains("KEY3")) { onKey(3, "KEY3") }
                    JumpCell("Jump 2", jump2bCount, isPurpleEnabled) { onJump(2, "JUMP2B") }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
private fun RowScope.JumpCell(label: String, count: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (enabled) vPrimary else mdOutlineVariant,
            contentColor = if (enabled) vPrimaryOn else mdOutline
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$count", fontSize = 18.sp)
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    label: String,
    points: Int,
    count: Int,
    enabled: Boolean,
    isActivated: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isActivated,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = when {
                isActivated -> vSuccess
                enabled -> Color(0xFFF500A1)
                else -> mdOutlineVariant
            },
            contentColor = when {
                isActivated -> vSuccessOn
                enabled -> Color.White
                else -> mdOutline
            }
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$points pt", fontWeight = FontWeight.Bold)
            Text("x$count", fontSize = 16.sp)
        }
    }
}

@Composable
private fun RowScope.SweetSpotCell(sweetSpotOn: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (sweetSpotOn) vSuccess else vPrimary,
            contentColor = if (sweetSpotOn) vSuccessOn else vPrimaryOn
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Text("Sweet Spot", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.SpacerCell() {
    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
}

@Composable
private fun FunKeyActionRow(
    sweetSpotOn: Boolean,
    onMiss: () -> Unit,
    onSweetSpot: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onMiss,
            colors = ButtonDefaults.buttonColors(backgroundColor = vWarning, contentColor = vWarningOn),
            modifier = Modifier.weight(1f)
        ) { Text("Miss+") }
        Button(
            onClick = onSweetSpot,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (sweetSpotOn) vSuccess else vPrimary,
                contentColor = if (sweetSpotOn) vSuccessOn else vPrimaryOn
            ),
            modifier = Modifier.weight(1f)
        ) { Text(if (sweetSpotOn) "Sweet Spot On" else "Sweet Spot") }
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(backgroundColor = mdPrimaryContainer, contentColor = mdPrimary),
            modifier = Modifier.weight(1f)
        ) { Text("Reset Score") }
    }
}

@Composable
private fun ParticipantQueueCard(
    activeParticipant: FunKeyParticipant?,
    participantQueue: List<FunKeyParticipant>,
    modifier: Modifier = Modifier
) {
    Card(elevation = 6.dp, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Teams", style = MaterialTheme.typography.h6)
            activeParticipant?.let {
                Text("Now Playing", fontSize = 12.sp, color = mdOutline)
                Text("${it.handler} & ${it.dog}", fontWeight = FontWeight.Bold)
                Text(it.utn, fontSize = 12.sp, color = mdOutline)
            } ?: Text("No active team", color = mdOutline)
            Text("Queue (${participantQueue.size})", fontSize = 12.sp, color = mdOutline)
            if (participantQueue.isEmpty()) {
                Text("No teams waiting", color = mdOutline)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(participantQueue) { index, participant ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text("${index + 1}. ${participant.handler} & ${participant.dog}", fontWeight = FontWeight.Medium)
                            Text(participant.utn, fontSize = 12.sp, color = mdOutline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunKeySidebar(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAddTeam: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onResetScore: () -> Unit,
    onClearState: () -> Unit
) {
    Card(elevation = 6.dp, modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Actions", style = MaterialTheme.typography.h6)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SidebarButton("Import", mdPrimary, Color.White, onImport, Modifier.weight(1f))
                    SidebarButton("Export", vPrimary, vPrimaryOn, onExport, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SidebarButton("Add Team", vSuccess, vSuccessOn, onAddTeam, Modifier.weight(1f))
                    SidebarButton("Reset Score", mdPrimaryContainer, mdPrimary, onResetScore, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SidebarButton("Next", Color(0xFFF500A1), Color.White, onNext, Modifier.weight(1f))
                    SidebarButton("Skip", Color(0xFF00B8D4), Color.White, onSkip, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SidebarButton("Clear State", Color(0xFFD50000), Color.White, onClearState, Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SidebarButton(
    text: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = background, contentColor = content),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
    ) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FunKeyAddParticipantDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var handler by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var utn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = handler, onValueChange = { handler = it }, label = { Text("Handler") }, singleLine = true)
                OutlinedTextField(value = dog, onValueChange = { dog = it }, label = { Text("Dog") }, singleLine = true)
                OutlinedTextField(value = utn, onValueChange = { utn = it }, label = { Text("UTN") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (handler.isNotBlank() && dog.isNotBlank() && utn.isNotBlank()) {
                        onAdd(handler.trim(), dog.trim(), utn.trim())
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(backgroundColor = mdOutlineVariant, contentColor = mdText)) {
                Text("Cancel")
            }
        }
    )
}

private fun exportParticipants(
    exporter: FileExporter,
    activeParticipant: FunKeyParticipant?,
    participantQueue: List<FunKeyParticipant>
) {
    val rows = buildList {
        activeParticipant?.let { add(it) }
        addAll(participantQueue)
    }
    val csv = buildString {
        appendLine("Handler,Dog,UTN")
        rows.forEach { participant ->
            appendLine("${participant.handler},${participant.dog},${participant.utn}")
        }
    }
    exporter.save("FunKeyParticipants.csv", csv.encodeToByteArray())
}
