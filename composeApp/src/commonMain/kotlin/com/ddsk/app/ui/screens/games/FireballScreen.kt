package com.ddsk.app.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.ddsk.app.persistence.rememberDataStore
import com.ddsk.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FireballScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { FireballScreenModel() }
        val dataStore = rememberDataStore()
        LaunchedEffect(Unit) {
            screenModel.initPersistence(dataStore)
        }

        val totalScore by screenModel.totalScore.collectAsState()
        val currentBoardScore by screenModel.currentBoardScore.collectAsState()
        val isFieldFlipped by screenModel.isFieldFlipped.collectAsState()
        val timerSecondsRemaining by screenModel.timerSecondsRemaining.collectAsState()
        val isTimerRunning by screenModel.isTimerRunning.collectAsState()
        val activeParticipant by screenModel.activeParticipant.collectAsState()
        val participantsQueue by screenModel.participantsQueue.collectAsState()
        val allRollersActive by screenModel.allRollersActive.collectAsState()
        val isFireballActive by screenModel.isFireballActive.collectAsState()
        val sweetSpotBonusAwarded by screenModel.sweetSpotBonusAwarded.collectAsState()
        val sidebarCollapsed by screenModel.sidebarCollapsed.collectAsState()
        val sidebarMessage by screenModel.sidebarMessage.collectAsState()
        val completedParticipants by screenModel.completedParticipants.collectAsState()

        val remainingParticipants = participantsQueue.filterNot { participant ->
            val hasScoring = participant.totalPoints > 0
                    || participant.nonFireballPoints > 0
                    || participant.fireballPoints > 0
                    || participant.completedBoards > 0
            hasScoring
        }

        var showClearParticipantsDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val fileExporter = rememberFileExporter()
        val assetLoader = rememberAssetLoader()

        // Hold the latest file import payload until the user chooses Add vs Replace.
        var pendingImport by remember { mutableStateOf<ImportResult?>(null) }
        var showImportChoiceDialog by remember { mutableStateOf(false) }

        val filePicker = rememberFilePicker { result ->
            when (result) {
                is ImportResult.Csv, is ImportResult.Xlsx -> {
                    pendingImport = result
                    showImportChoiceDialog = true
                }
                else -> {}
            }
        }

        if (showImportChoiceDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportChoiceDialog = false
                    pendingImport = null
                },
                title = { Text("Import participants") },
                text = { Text("Do you want to add the new participants to the existing list, or delete all existing participants and replace them?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val import = pendingImport
                            showImportChoiceDialog = false
                            pendingImport = null
                            if (import != null) {
                                scope.launch {
                                    when (import) {
                                        is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, FireballScreenModel.ImportMode.Add)
                                        is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, FireballScreenModel.ImportMode.Add)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    ) { Text("Add") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val import = pendingImport
                                showImportChoiceDialog = false
                                pendingImport = null
                                if (import != null) {
                                    scope.launch {
                                        when (import) {
                                            is ImportResult.Csv -> screenModel.importParticipantsFromCsv(import.contents, FireballScreenModel.ImportMode.ReplaceAll)
                                            is ImportResult.Xlsx -> screenModel.importParticipantsFromXlsx(import.bytes, FireballScreenModel.ImportMode.ReplaceAll)
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        ) { Text("Replace") }
                        TextButton(
                            onClick = {
                                showImportChoiceDialog = false
                                pendingImport = null
                            }
                        ) { Text("Cancel") }
                    }
                }
            )
        }

        val pendingJsonExport by screenModel.pendingJsonExport.collectAsState()

        LaunchedEffect(pendingJsonExport) {
            val pending = pendingJsonExport ?: return@LaunchedEffect
            // Multiplatform: always prompt user to choose a save location where possible.
            saveJsonFileWithPicker(pending.filename, pending.content)
            screenModel.consumePendingJsonExport()
        }

        Surface(color = Palette.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                HeaderRow(
                    timerRunning = isTimerRunning,
                    secondsRemaining = timerSecondsRemaining,
                    onTimerToggle = {
                        if (isTimerRunning) screenModel.stopRoundTimer() else screenModel.startRoundTimer(64)
                    },
                    activeParticipant = activeParticipant,
                    totalScore = totalScore,
                    boardScore = currentBoardScore,
                    onAllRollers = screenModel::toggleAllRollers,
                    allRollers = allRollersActive,
                )

                Spacer(Modifier.height(16.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val spacing = 12.dp
                    val sidebarWidth = if (sidebarCollapsed) 68.dp else 190.dp
                    val participantWidth = 220.dp
                    val minFieldWidth = 260.dp
                    val availableFieldWidth = (maxWidth - sidebarWidth - participantWidth - spacing * 2).coerceAtLeast(minFieldWidth)
                    val fieldHeight = (availableFieldWidth / 1.8f).coerceAtLeast(340.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.Top
                    ) {
                        SidebarColumn(
                            collapsed = sidebarCollapsed,
                            onToggle = screenModel::toggleSidebar,
                            onImport = { filePicker.launch() },
                            onExport = {
                                scope.launch {
                                    val templateBytes = assetLoader.load("templates/UDC Fireball Data Entry L1 Div Sort.xlsx")
                                    if (templateBytes == null) {
                                        screenModel.sidebarMessage.value = "Template missing: UDC Fireball Data Entry L1 Div Sort.xlsx"
                                        return@launch
                                    }

                                    // React behavior: export committed participant totals (completed rounds).
                                    // Active participant may have in-progress board state and should not be included unless
                                    // the user has advanced with Next (which commits and adds to completedParticipants).
                                    val participants = completedParticipants

                                    if (participants.isEmpty()) {
                                        screenModel.sidebarMessage.value = "Export: no completed participants yet (press Next to commit a round)"
                                        return@launch
                                    }

                                    val firstNonZero = participants.firstOrNull {
                                        it.totalPoints != 0 || it.nonFireballPoints != 0 || it.fireballPoints != 0 || (it.highestZone ?: 0) != 0
                                    }
                                    screenModel.sidebarMessage.value = if (firstNonZero == null) {
                                        "Export debug: completed participants still look zero (count=${participants.size})"
                                    } else {
                                        "Export debug (completed): ${firstNonZero.handler}/${firstNonZero.dog} NF=${firstNonZero.nonFireballPoints} FB=${firstNonZero.fireballPoints} SS=${firstNonZero.sweetSpotBonus} HZ=${firstNonZero.highestZone ?: 0} TOT=${firstNonZero.totalPoints}"
                                    }

                                    val exported = generateFireballXlsx(participants, templateBytes)
                                    if (exported.isEmpty()) {
                                        screenModel.sidebarMessage.value = "Export failed"
                                        return@launch
                                    }

                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    val stamp = fireballTimestamp(now)

                                    fileExporter.save("Fireball_Scores_$stamp.xlsx", exported)
                                    screenModel.sidebarMessage.value = "Exported Fireball_Scores_$stamp.xlsx"
                                }
                            },
                            onHelp = screenModel::openHelp,
                            onClearBoard = screenModel::clearBoard,
                            onResetGame = screenModel::resetGame,
                            onFlipField = screenModel::toggleFieldOrientation,
                            onToggleFireball = screenModel::toggleFireball,
                            onToggleSweetSpot = screenModel::toggleManualSweetSpot,
                            onNext = screenModel::nextParticipant,
                            onSkip = screenModel::skipParticipant,
                            onAddTeam = { screenModel.addParticipant(FireballParticipant("New", "Dog", "UTN")) },
                            collapsedMessage = sidebarMessage,
                            isFireballActive = isFireballActive,
                            sweetSpotActive = sweetSpotBonusAwarded,
                            remainingCount = remainingParticipants.size,
                            onCollapseRequested = screenModel::toggleSidebar
                        )

                        ParticipantPanel(
                            remaining = remainingParticipants,
                            height = fieldHeight,
                        )

                        FieldAndControls(
                            screenModel = screenModel,
                            isFieldFlipped = isFieldFlipped,
                            isFireballActive = isFireballActive,
                            sweetSpotActive = sweetSpotBonusAwarded,
                            modifier = Modifier
                                .width(availableFieldWidth)
                                .height(fieldHeight)
                        )
                    }
                }
            }
        }

        if (showClearParticipantsDialog) {
            AlertDialog(
                onDismissRequest = { showClearParticipantsDialog = false },
                title = { Text("Clear All Participants?") },
                text = { Text("This will remove every participant from the queue. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.clearParticipantsQueue()
                        showClearParticipantsDialog = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearParticipantsDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun HeaderRow(
    timerRunning: Boolean,
    secondsRemaining: Int,
    onTimerToggle: () -> Unit,
    activeParticipant: FireballParticipant?,
    totalScore: Int,
    boardScore: Int,
    onAllRollers: () -> Unit,
    allRollers: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Fire Ball", style = MaterialTheme.typography.h5, color = Palette.onSurface)
            Text("Total Score: $totalScore", style = MaterialTheme.typography.subtitle1, color = Palette.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTimerToggle,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (timerRunning) Palette.error else Palette.info,
                    contentColor = Color.White
                )
            ) {
                Text(if (timerRunning) "Stop (${secondsRemaining}s)" else "Start Timer")
            }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(10.dp)
                    .border(1.dp, Palette.outlineVariant, RoundedCornerShape(4.dp))
            ) {
                val pct = (secondsRemaining / 64f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(10.dp)
                        .background(Palette.primaryContainer, RoundedCornerShape(4.dp))
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Active:", style = MaterialTheme.typography.caption, color = Palette.onBackground)
            Text(
                activeParticipant?.let { "${it.handler} & ${it.dog}" } ?: "No team loaded",
                style = MaterialTheme.typography.body1,
                color = Palette.onSurface
            )
            Text("Board: $boardScore", style = MaterialTheme.typography.caption, color = Palette.onBackground)
        }
        Button(
            onClick = onAllRollers,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (allRollers) Palette.success else Palette.primary,
                contentColor = Palette.onPrimary
            )
        ) {
            Text("All Rollers")
        }
    }
}

@Composable
private fun SidebarColumn(
    collapsed: Boolean,
    onToggle: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onHelp: () -> Unit,
    onClearBoard: () -> Unit,
    onResetGame: () -> Unit,
    onFlipField: () -> Unit,
    onToggleFireball: () -> Unit,
    onToggleSweetSpot: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAddTeam: () -> Unit,
    collapsedMessage: String,
    isFireballActive: Boolean,
    sweetSpotActive: Boolean,
    remainingCount: Int,
    onCollapseRequested: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Palette.surfaceContainer,
        elevation = 6.dp,
        modifier = Modifier
            .width(if (collapsed) 68.dp else 190.dp)
            .padding(vertical = 2.dp)
    ) {
        if (collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                TextButton(onClick = onToggle, modifier = Modifier.padding(0.dp)) {
                    Text("☰", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCollapseRequested,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(text = "Expand")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    collapsedMessage,
                    style = MaterialTheme.typography.caption,
                    color = Palette.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                TextButton(onClick = onToggle, modifier = Modifier.align(Alignment.End)) {
                    Text("Hide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.info, contentColor = Palette.onInfo),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.info, contentColor = Palette.onInfo),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onHelp,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.primaryContainer, contentColor = Palette.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Help") }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onClearBoard,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.tertiary, contentColor = Palette.onTertiary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear Board") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onResetGame,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.error, contentColor = Palette.onError),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset Game") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onFlipField,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Flip Field") }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onToggleFireball,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isFireballActive) Palette.warning else Palette.primary,
                        contentColor = if (isFireballActive) Palette.onWarning else Palette.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fireball Mode") }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Next") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.tertiary, contentColor = Palette.onTertiary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Skip") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAddTeam,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Palette.info, contentColor = Palette.onInfo),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Team") }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Remaining: $remainingCount",
                    style = MaterialTheme.typography.caption,
                    color = Palette.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ParticipantPanel(
    remaining: List<FireballParticipant>,
    height: Dp,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Palette.surfaceContainer,
        elevation = 6.dp,
        modifier = Modifier
            .width(220.dp)
            .height(height)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                "Remaining (${remaining.size})",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Palette.onSurface
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(remaining, key = { "${it.handler}-${it.dog}-${it.utn}" }) { participant ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(participant.handler, fontWeight = FontWeight.Bold, color = Palette.onSurface)
                        Text(participant.dog, style = MaterialTheme.typography.body2, color = Palette.onSurfaceVariant)
                        Text("UTN: ${participant.utn}", style = MaterialTheme.typography.caption, color = Palette.onSurfaceVariant)
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun FieldAndControls(
    screenModel: FireballScreenModel,
    isFieldFlipped: Boolean,
    isFireballActive: Boolean,
    sweetSpotActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FireballGrid(screenModel, isFieldFlipped, Modifier.weight(1f, fill = true).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = screenModel::toggleManualSweetSpot,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (sweetSpotActive) Palette.success else Palette.info,
                contentColor = if (sweetSpotActive) Palette.onSuccess else Palette.onInfo
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (sweetSpotActive) "Sweet Spot Bonus (On)" else "Sweet Spot Bonus", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FireballGrid(
    screenModel: FireballScreenModel,
    isFieldFlipped: Boolean,
    modifier: Modifier = Modifier
) {
    val clickedZones by screenModel.clickedZones.collectAsState()
    val fireballZones by screenModel.fireballZones.collectAsState()
    val rowIndices = FireballScreenModel.zoneValueGrid.indices.toList()
    val colIndices = FireballScreenModel.zoneValueGrid.first().indices.toList()
    val rowOrder = if (isFieldFlipped) rowIndices.reversed() else rowIndices
    val colOrder = if (isFieldFlipped) colIndices.reversed() else colIndices

    Card(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Palette.surfaceContainer,
        elevation = 8.dp,
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val horizontalSpacing = 8.dp
            val verticalSpacing = 8.dp
            val availableHeight = (maxHeight - verticalSpacing * 2).coerceAtLeast(0.dp)
            val unitHeight = availableHeight / 4f
            val middleRowHeight = (unitHeight * 2f).coerceAtLeast(96.dp)
            val compactRowHeight = unitHeight.coerceAtLeast(72.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                rowOrder.forEach { row ->
                    val rowHeight = if (row == 1) middleRowHeight else compactRowHeight
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                    ) {
                        colOrder.forEach { col ->
                            val zoneValue = FireballScreenModel.zoneValue(row, col)
                            val point = FireballGridPoint(row, col)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Palette.surface)
                                    .border(1.dp, Palette.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (zoneValue != null) {
                                    ZoneButton(
                                        value = zoneValue,
                                        clicked = clickedZones.contains(point),
                                        fireball = fireballZones.contains(point),
                                        onClick = { screenModel.handleZoneClick(row, col) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ZoneButton(value: Int, clicked: Boolean, fireball: Boolean, onClick: () -> Unit) {
    val colors = when {
        fireball -> ButtonDefaults.buttonColors(backgroundColor = Palette.warning, contentColor = Palette.onWarning)
        clicked -> ButtonDefaults.buttonColors(backgroundColor = Palette.success, contentColor = Palette.onSuccess)
        else -> ButtonDefaults.buttonColors(backgroundColor = Palette.primary, contentColor = Palette.onPrimary)
    }
    Button(
        onClick = onClick,
        // Allow toggling/clearing when Fireball Mode is active.
        // The screen model itself enforces the rules when Fireball Mode is NOT active.
        enabled = true,
        colors = colors,
        modifier = Modifier.fillMaxSize().padding(6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

private fun fireballTimestamp(now: kotlinx.datetime.LocalDateTime): String {
    fun pad2(n: Int) = n.toString().padStart(2, '0')
    return buildString {
        append(now.year)
        append(pad2(now.monthNumber))
        append(pad2(now.dayOfMonth))
        append('_')
        append(pad2(now.hour))
        append(pad2(now.minute))
        append(pad2(now.second))
    }
}
