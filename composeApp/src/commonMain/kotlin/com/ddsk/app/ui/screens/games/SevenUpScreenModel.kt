package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.time.TimeSource

@Serializable
data class SevenUpParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    // scoring/export fields (optional)
    val jumpSum: Int = 0,
    val nonJumpSum: Int = 0,
    val timeRemaining: Float = 60.0f,
    val sweetSpotBonus: Boolean = false,
    val allRollers: Boolean = false,
    val heightDivision: String = ""
)

@Serializable
data class SevenUpUiState(
    val score: Int = 0,
    val jumpCounts: Map<String, Int> = emptyMap(),
    val disabledJumps: Set<String> = emptySet(),
    val jumpStreak: Int = 0,
    val markedCells: Map<String, Int> = emptyMap(),
    val nonJumpMark: Int = 1,
    val lastWasNonJump: Boolean = false,
    val sweetSpotBonus: Boolean = false,
    val hasStarted: Boolean = false,
    val rectangleVersion: Int = 0,
    val isFlipped: Boolean = false,
    val timeRemaining: Float = 60.0f,
    val isTimerRunning: Boolean = false,
    val activeParticipant: SevenUpParticipant? = null,
    val queue: List<SevenUpParticipant> = emptyList(),
    val completed: List<SevenUpParticipant> = emptyList(),
    // Per-participant logs, keyed by "handler-dog" (persisted)
    val participantLogs: Map<String, List<String>> = emptyMap(),
    // Current round log entries (persisted so Next export matches what user saw)
    val currentRoundLog: List<String> = emptyList(),
)

class SevenUpScreenModel : ScreenModel {

    enum class ImportMode { Add, ReplaceAll }

    // Replacing separate flows with centralized UI state flow for persistence consistency
    private val _uiState = MutableStateFlow(SevenUpUiState())
    // Need to expose individual flows if UI consumes them individually, OR refactor UI to consume state object.
    // Keeping existing separate flow variables as derived properties OR computed flows to minimize UI refactor.

    // Actually, simply adding persistence to existing variables might be complex if I don't group them.
    // Let's proxy them.
    val score = _uiState.map { it.score }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val jumpCounts = _uiState.map { it.jumpCounts }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())
    val disabledJumps = _uiState.map { it.disabledJumps }.stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())
    val jumpStreak = _uiState.map { it.jumpStreak }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val markedCells = _uiState.map { it.markedCells }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())
    val nonJumpMark = _uiState.map { it.nonJumpMark }.stateIn(screenModelScope, SharingStarted.Eagerly, 1)
    val lastWasNonJump = _uiState.map { it.lastWasNonJump }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val sweetSpotBonus = _uiState.map { it.sweetSpotBonus }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val hasStarted = _uiState.map { it.hasStarted }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val rectangleVersion = _uiState.map { it.rectangleVersion }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val isFlipped = _uiState.map { it.isFlipped }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val timeRemaining = _uiState.map { it.timeRemaining }.stateIn(screenModelScope, SharingStarted.Eagerly, 60.0f)
    val isTimerRunning = _uiState.map { it.isTimerRunning }.stateIn(screenModelScope, SharingStarted.Eagerly, false)

    // New flows for participants
    val participants = _uiState.map { it.queue }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())
    val activeParticipant = _uiState.map { it.activeParticipant }.stateIn(screenModelScope, SharingStarted.Eagerly, null)

    private val _pendingJsonExport = MutableStateFlow<SevenUpPendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<SevenUpPendingJsonExport?> = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Public helper for export filenames (kept consistent with other games). */
    fun exportStampForFilename(now: kotlinx.datetime.LocalDateTime): String = exportStamp(now)

    private fun exportStamp(now: kotlinx.datetime.LocalDateTime): String {
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

    private fun participantKey(p: SevenUpParticipant): String = "${p.handler}-${p.dog}"

    fun logEvent(message: String) {
        val ts = Clock.System.now().toString()
        val active = _uiState.value.activeParticipant
        val key = active?.let { participantKey(it) }
        val line = "$ts: $message"

        _uiState.update { s ->
            val updatedRound = (s.currentRoundLog + line).takeLast(300)
            val updatedParticipantLogs = if (key == null) {
                s.participantLogs
            } else {
                val existing = s.participantLogs[key].orEmpty()
                s.participantLogs + (key to (existing + line).takeLast(2000))
            }

            s.copy(
                currentRoundLog = updatedRound,
                participantLogs = updatedParticipantLogs
            )
        }
        persistState()
    }

    private fun resetRoundStateOnly() {
        _uiState.update {
            it.copy(
                score = 0,
                jumpCounts = emptyMap(),
                disabledJumps = emptySet(),
                jumpStreak = 0,
                markedCells = emptyMap(),
                nonJumpMark = 1,
                lastWasNonJump = false,
                sweetSpotBonus = false,
                hasStarted = false,
                timeRemaining = 60.0f,
                isTimerRunning = false,
                currentRoundLog = emptyList(),
            )
        }
    }

    private val _allRollers = MutableStateFlow(false)
    val allRollers = _allRollers.asStateFlow()

    fun setAllRollers(enabled: Boolean) {
        _allRollers.value = enabled
        persistState()
    }

    fun toggleAllRollersFlag() {
        setAllRollers(!_allRollers.value)
    }

    fun previousParticipant() {
        val state = _uiState.value
        val active = state.activeParticipant
        val queue = state.queue
        if (active == null && queue.isEmpty()) return

        // React behavior: move last team to front of queue
        val combined = buildList {
            active?.let { add(it) }
            addAll(queue)
        }
        if (combined.size <= 1) return

        val last = combined.last()
        val rotated = listOf(last) + combined.dropLast(1)

        _uiState.update {
            it.copy(
                activeParticipant = rotated.firstOrNull(),
                queue = rotated.drop(1)
            )
        }
        resetRoundStateOnly()
        _allRollers.value = false
        logEvent("Previous: Now active - ${last.handler} & ${last.dog}")
        persistState()
    }

    fun skipParticipant() {
        val state = _uiState.value
        val active = state.activeParticipant ?: return
        val queue = state.queue

        // React behavior: move current team to end without scoring
        val rotated = queue + active
        val newActive = rotated.firstOrNull()

        _uiState.update {
            it.copy(
                activeParticipant = newActive,
                queue = rotated.drop(1)
            )
        }
        resetRoundStateOnly()
        _allRollers.value = false
        logEvent("Skip: Skipped ${active.handler} & ${active.dog}")
        persistState()
    }

    fun nextParticipant() {
        val state = _uiState.value
        val active = state.activeParticipant

        if (active == null && state.queue.isEmpty()) return

        if (active != null) {
            val jumpSum = state.jumpCounts.values.sum()
            val nonJumpSum = state.markedCells.size

            val completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            val result = SevenUpRoundResult(
                finalScore = state.score,
                timeRemaining = state.timeRemaining,
                jumpSum = jumpSum,
                nonJumpSum = nonJumpSum,
                sweetSpotBonus = state.sweetSpotBonus,
                allRollers = _allRollers.value,
                completedAt = completedAt.toString(),
                heightDivision = active.heightDivision
            )

            // JSON export emit (match React structure; include log)
            runCatching {
                val stamp = exportStamp(completedAt)
                val safeHandler = active.handler.replace("\\s+".toRegex(), "")
                val safeDog = active.dog.replace("\\s+".toRegex(), "")
                val filename = "7Up_${safeHandler}_${safeDog}_$stamp.json"

                val export = SevenUpRoundExport(
                    gameMode = "7-Up",
                    exportTimestamp = Clock.System.now().toString(),
                    participantData = SevenUpParticipantData(
                        handler = active.handler,
                        dog = active.dog,
                        utn = active.utn,
                        completedAt = completedAt.toString()
                    ),
                    roundResults = result,
                    logEntries = state.participantLogs[participantKey(active)].orEmpty()
                )

                _pendingJsonExport.value = SevenUpPendingJsonExport(filename, exportJson.encodeToString(export))
            }

            // Persist completed participant with baked-in export fields
            _uiState.update { s ->
                val completedParticipant = SevenUpParticipant(
                    handler = active.handler,
                    dog = active.dog,
                    utn = active.utn,
                    jumpSum = jumpSum,
                    nonJumpSum = nonJumpSum,
                    timeRemaining = state.timeRemaining,
                    sweetSpotBonus = state.sweetSpotBonus,
                    allRollers = _allRollers.value,
                    heightDivision = active.heightDivision
                )
                s.copy(
                    completed = s.completed + completedParticipant,
                    activeParticipant = s.queue.firstOrNull() ?: SevenUpParticipant("", "", ""),
                    queue = if (s.queue.isEmpty()) emptyList() else s.queue.drop(1)
                )
            }
        }

        // reset round state and all-rollers for next competitor
        resetRoundStateOnly()
        _allRollers.value = false
        logEvent("Next")
        persistState()
    }

    /**
     * Export snapshot used for XLSM export: completed + active(with current round state applied) + queue.
     */
    fun exportSnapshot(): List<SevenUpParticipant> {
        val state = _uiState.value
        val active = state.activeParticipant
        val jumpSum = state.jumpCounts.values.sum()
        val nonJumpSum = state.markedCells.size

        val activeExport = active?.takeIf { it.handler.isNotBlank() || it.dog.isNotBlank() }?.copy(
            jumpSum = jumpSum,
            nonJumpSum = nonJumpSum,
            timeRemaining = state.timeRemaining,
            sweetSpotBonus = state.sweetSpotBonus,
            allRollers = _allRollers.value
        )

        return buildList {
            addAll(state.completed)
            if (activeExport != null) add(activeExport)
            addAll(state.queue)
        }
    }

    fun exportScoresXlsm(templateBytes: ByteArray): ByteArray {
        return generateSevenUpXlsm(exportSnapshot(), templateBytes)
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode) {
        val imported = parseCsv(csvText)
        val players = imported.map { SevenUpParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players, mode)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray, mode: ImportMode) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { SevenUpParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players, mode)
    }

    private fun applyImportedPlayers(players: List<SevenUpParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return
        _uiState.update { state ->
            val existing = buildList {
                state.activeParticipant?.let { add(it) }
                addAll(state.queue)
            }

            val merged = when (mode) {
                ImportMode.ReplaceAll -> players
                ImportMode.Add -> existing + players
            }

            val newActive = merged.firstOrNull() ?: SevenUpParticipant("", "", "")
            state.copy(
                activeParticipant = newActive,
                queue = merged.drop(1),
                completed = emptyList()
            )
        }
        resetRoundStateOnly()
        _allRollers.value = false
        persistState()
    }

    fun clearParticipants() {
        _uiState.update {
            it.copy(
                activeParticipant = null,
                queue = emptyList(),
                completed = emptyList(),
                participantLogs = emptyMap(),
                currentRoundLog = emptyList()
            )
        }
        persistState()
    }

    private var timerJob: Job? = null
    private var dataStore: DataStore? = null
    private val persistenceKey = "SevenUpData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<SevenUpUiState>(json)
                    _uiState.value = saved
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun persistState() {
        val store = dataStore ?: return
        val state = _uiState.value
        screenModelScope.launch {
            try {
                val json = Json.encodeToString(state)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleCellPress(label: String, row: Int, col: Int) {
        val cellKey = "$row,$col"
        if (label.startsWith("Jump")) {
            if (!_uiState.value.hasStarted) _uiState.update { it.copy(hasStarted = true) }
            if (label in _uiState.value.disabledJumps) return
            if (_uiState.value.jumpStreak >= 3) return

            _uiState.update {
                it.copy(
                    jumpStreak = it.jumpStreak + 1,
                    disabledJumps = it.disabledJumps + label,
                    jumpCounts = it.jumpCounts + (label to (it.jumpCounts[label] ?: 0) + 1),
                    score = it.score + 3,
                    lastWasNonJump = false
                )
            }

        } else { // Non-jump cell
            if (!_uiState.value.hasStarted || _uiState.value.lastWasNonJump) return
            if (cellKey in _uiState.value.markedCells) return
            if (_uiState.value.nonJumpMark > 5) return

            _uiState.update { state ->
                val newScore = state.nonJumpMark.let { if (it == 5 && label == "Sweet Spot") it + 7 else 1 }
                state.copy(
                    markedCells = state.markedCells + (cellKey to state.nonJumpMark),
                    score = state.score + newScore,
                    nonJumpMark = state.nonJumpMark + 1,
                    jumpStreak = 0,
                    disabledJumps = emptySet(),
                    lastWasNonJump = true
                )
            }
        }
        persistState()
    }

    fun startTimer() {
        if (!_uiState.value.isTimerRunning) {
            _uiState.update { it.copy(isTimerRunning = true) }
            val startTimeMark = TimeSource.Monotonic.markNow()
            val initialTimeOnStart = _uiState.value.timeRemaining
            timerJob = screenModelScope.launch {
                while (_uiState.value.isTimerRunning) {
                    val elapsedSeconds = startTimeMark.elapsedNow().inWholeMilliseconds / 1000f
                    _uiState.update {
                        it.copy(
                            timeRemaining = max(0f, initialTimeOnStart - elapsedSeconds)
                        )
                    }
                    if (_uiState.value.timeRemaining <= 0f) stopTimer()
                    delay(10L)
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false, score = it.score + it.timeRemaining.toInt()) }
        persistState()
    }

    fun setTimeManually(timeStr: String) {
        val newTime = timeStr.toFloatOrNull() ?: return
        timerJob?.cancel()
        _uiState.update { it.copy(timeRemaining = newTime, isTimerRunning = false, score = it.score + newTime.toInt()) }
        persistState()
    }

    fun cycleRectangleVersion() {
        if (!_uiState.value.hasStarted) {
            _uiState.update { it.copy(rectangleVersion = (it.rectangleVersion + 1) % 11) }
            persistState()
        }
    }

    fun flipField() {
        _uiState.update { it.copy(isFlipped = !_uiState.value.isFlipped) }
        persistState()
    }

    fun reset() {
        _uiState.value = SevenUpUiState() // Reset to default state
        timerJob?.cancel()
        _allRollers.value = false
        persistState()
    }
}

@kotlinx.serialization.Serializable
private data class SevenUpRoundResult(
    val finalScore: Int,
    val timeRemaining: Float,
    val jumpSum: Int,
    val nonJumpSum: Int,
    val sweetSpotBonus: Boolean,
    val allRollers: Boolean,
    val completedAt: String,
    val heightDivision: String = ""
)

@kotlinx.serialization.Serializable
data class SevenUpPendingJsonExport(val filename: String, val content: String)

@kotlinx.serialization.Serializable
private data class SevenUpParticipantData(
    val handler: String,
    val dog: String,
    val utn: String,
    val completedAt: String
)

@kotlinx.serialization.Serializable
private data class SevenUpRoundExport(
    val gameMode: String,
    val exportTimestamp: String,
    val participantData: SevenUpParticipantData,
    val roundResults: SevenUpRoundResult,
    // Per-participant log (React: logEntries)
    val logEntries: List<String> = emptyList()
)
