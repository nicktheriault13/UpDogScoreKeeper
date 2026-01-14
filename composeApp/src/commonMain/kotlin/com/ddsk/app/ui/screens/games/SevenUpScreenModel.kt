package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
data class JumpState(val clicked: Boolean = false, val disabled: Boolean = false)

@Serializable
data class NonJumpState(val clicked: Boolean = false)

sealed class SevenUpCell {
    abstract val label: String
    data class Jump(val id: String, override val label: String) : SevenUpCell()
    data class NonJump(override val label: String, val isSweetSpot: Boolean = false) : SevenUpCell()
    object Empty : SevenUpCell() { override val label: String = "" }
}

@Serializable
data class SevenUpParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
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
    // Track which jump buttons should be highlighted as 'clicked' since the last non-jump.
    val jumpsClickedSinceLastNonJump: Set<String> = emptySet(),
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
    // Tracks how many manual time edits have occurred this round. Used to prevent doubling score additions
    // when the user edits time multiple times.
    val manualTimeEdits: Int = 0,
    val activeParticipant: SevenUpParticipant? = null,
    val queue: List<SevenUpParticipant> = emptyList(),
    val completed: List<SevenUpParticipant> = emptyList(),
    val participantLogs: Map<String, List<String>> = emptyMap(),
    val currentRoundLog: List<String> = emptyList(),
    val allRollers: Boolean = false
) {
    val currentScore: Int get() = score
    val jumpsClickedCount: Int get() = jumpCounts.values.sum()
    val nonJumpsClickedCount: Int get() = markedCells.size
    val sweetSpotBonusActive: Boolean get() = sweetSpotBonus
    val gridVersion: Int get() = rectangleVersion
    val isFieldFlipped: Boolean get() = isFlipped
    // React behavior: non-jumps are disabled until started, and you can't do two non-jumps in a row.
    // (Additional per-cell checks like already-marked and nonJumpMark>5 are handled in UI by active/marked state and in model in handleCellPress.)
    val nonJumpDisabled: Boolean get() = !hasStarted || lastWasNonJump || nonJumpMark > 5

    val jumpState: Map<String, JumpState> get() {
        // clicked is based on the per-round highlight set (resets on non-jump),
        // while disabled is based on disabledJumps.
        val ids = (jumpCounts.keys + disabledJumps + jumpsClickedSinceLastNonJump).toSet()
        return ids.associateWith { id ->
            JumpState(
                clicked = jumpsClickedSinceLastNonJump.contains(id),
                disabled = disabledJumps.contains(id)
            )
        }
    }

    val nonJumpState: Map<String, NonJumpState> get() {
        return markedCells.keys.associateWith { NonJumpState(clicked = true) }
    }
}

class SevenUpScreenModel : ScreenModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    enum class ImportMode { Add, ReplaceAll }

    private val _uiState = MutableStateFlow(SevenUpUiState())
    val uiState = _uiState.asStateFlow()

    // Exposed Flows for UI (aliased)
    val score = _uiState.map { it.score }.stateIn(scope, SharingStarted.Eagerly, 0)
    val timerRunning = _uiState.map { it.isTimerRunning }.stateIn(scope, SharingStarted.Eagerly, false)
    val timeLeft = _uiState.map { it.timeRemaining }.stateIn(scope, SharingStarted.Eagerly, 60.0f)

    // Audio Timer
    val audioTimerPlaying = MutableStateFlow(false)
    val audioTimerPosition = MutableStateFlow(0L)
    val audioTimerDuration = MutableStateFlow(60000L)

    fun toggleAudioTimer() {
        audioTimerPlaying.update { !it }
    }

    private val _pendingJsonExport = MutableStateFlow<SevenUpPendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<SevenUpPendingJsonExport?> = _pendingJsonExport.asStateFlow()

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private var timerJob: Job? = null
    private var dataStore: DataStore? = null
    private val persistenceKey = "SevenUpData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
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
        scope.launch {
            try {
                val json = Json.encodeToString(state)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            s.copy(currentRoundLog = updatedRound, participantLogs = updatedParticipantLogs)
        }
        persistState()
    }

    private fun roundToHundredths(value: Float): Float {
        return kotlin.math.round(value * 100f) / 100f
    }

    private fun roundToHundredths(value: Double): Double { // Overload for double
        return kotlin.math.round(value * 100.0) / 100.0.toFloat().toDouble() // Simplify
    }

    private fun resetRoundStateOnly() {
        _uiState.update {
            it.copy(
                score = 0,
                jumpCounts = emptyMap(),
                disabledJumps = emptySet(),
                jumpsClickedSinceLastNonJump = emptySet(),
                jumpStreak = 0,
                markedCells = emptyMap(),
                nonJumpMark = 1,
                lastWasNonJump = false,
                sweetSpotBonus = false,
                hasStarted = false,
                timeRemaining = 60.0f,
                isTimerRunning = false,
                manualTimeEdits = 0,
                currentRoundLog = emptyList(),
                allRollers = false
            )
        }
    }

    fun setAllRollers(enabled: Boolean) {
        _uiState.update { it.copy(allRollers = enabled) }
        persistState()
    }

    fun toggleAllRollers() {
        setAllRollers(!_uiState.value.allRollers)
    }

    fun toggleAllRollersFlag() = toggleAllRollers()

    fun exportData(templateBytes: ByteArray) {
        // Placeholder
        logEvent("Export Data")
    }

    fun exportLog() {
        // Placeholder
        logEvent("Export Log")
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val p = SevenUpParticipant(handler, dog, utn)
        _uiState.update {
             if (it.activeParticipant == null) it.copy(activeParticipant = p)
             else it.copy(queue = it.queue + p)
        }
        persistState()
    }

    fun setManualTime(time: Double) {
        setTimeManually(time.toString())
    }

    fun setTimeManually(timeStr: String) {
        val newTimeRaw = timeStr.toFloatOrNull() ?: return
        val newTime = roundToHundredths(newTimeRaw)

        timerJob?.cancel()

        _uiState.update { state ->
            // Requirement: "when it is editted then add the value entered rounded to the nearest whole number to the score"
            // If they edit multiple times, add the *delta* from the last edit to avoid double-counting.
            val enteredPoints = kotlin.math.round(newTime).toInt()

            val priorEdits = state.manualTimeEdits
            val priorPointsAccounted = if (priorEdits <= 0) 0 else kotlin.math.round(state.timeRemaining).toInt()
            val delta = enteredPoints - priorPointsAccounted

            state.copy(
                timeRemaining = newTime,
                isTimerRunning = false,
                manualTimeEdits = priorEdits + 1,
                score = state.score + delta
            )
        }
        persistState()
    }

    fun startCountdown() = startTimer()
    fun stopCountdown() = stopTimer()

    fun startTimer() {
        if (!_uiState.value.isTimerRunning) {
            _uiState.update { it.copy(isTimerRunning = true) }
            val startTimeMark = TimeSource.Monotonic.markNow()
            val initialTimeOnStart = _uiState.value.timeRemaining
            timerJob = scope.launch {
                while (_uiState.value.isTimerRunning) {
                    val elapsedSeconds = startTimeMark.elapsedNow().inWholeMilliseconds / 1000f
                    _uiState.update {
                        val raw = max(0f, initialTimeOnStart - elapsedSeconds)
                        it.copy(timeRemaining = roundToHundredths(raw))
                    }
                    if (_uiState.value.timeRemaining <= 0f) stopTimer()
                    delay(10L)
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { state ->
            val roundedForScore = kotlin.math.round(state.timeRemaining).toInt()
            state.copy(
                isTimerRunning = false,
                timeRemaining = roundToHundredths(state.timeRemaining),
                score = state.score + roundedForScore
            )
        }
        persistState()
    }

    fun undo() {
        logEvent("Undo Request (Not Implemented)")
    }

    fun toggleGridVersion() {
         if (!_uiState.value.hasStarted) {
            _uiState.update { it.copy(rectangleVersion = (it.rectangleVersion + 1) % 11) }
            persistState()
        }
    }

    private fun normalizeJumpId(label: String): String? {
        // Accept both UI labels ("Jump1") and ids ("J1") to stay robust.
        val cleaned = label.trim()
        val jMatch = Regex("\\bJ([1-7])\\b").find(cleaned)
        if (jMatch != null) return "J" + jMatch.groupValues[1]

        // "Jump1" or "Jump 1"
        val jumpMatch = Regex("Jump\\s*([1-7])").find(cleaned)
        if (jumpMatch != null) return "J" + jumpMatch.groupValues[1]

        return null
    }

    fun handleGridClick(row: Int, col: Int, cell: SevenUpCell) {
        when (cell) {
            // IMPORTANT: use the stable id for jumps so scoring/disable state lines up with UI lookups.
            is SevenUpCell.Jump -> handleCellPress(cell.id, row, col)
            is SevenUpCell.NonJump -> handleCellPress(if (cell.isSweetSpot) "Sweet Spot" else "", row, col)
            else -> return
        }
    }

    private fun handleCellPress(label: String, row: Int, col: Int) {
        val cellKey = "$row,$col"
        val jumpId = normalizeJumpId(label)

        if (jumpId != null) {
            if (!_uiState.value.hasStarted) {
                _uiState.update { state ->
                    state.copy(
                        hasStarted = true,
                        jumpStreak = 1,
                        disabledJumps = setOf(jumpId),
                        jumpsClickedSinceLastNonJump = setOf(jumpId),
                        jumpCounts = state.jumpCounts + (jumpId to ((state.jumpCounts[jumpId] ?: 0) + 1)),
                        score = state.score + 3,
                        lastWasNonJump = false
                    )
                }
                persistState()
                return
            }

            if (jumpId in _uiState.value.disabledJumps) return
            if (_uiState.value.jumpStreak >= 3) return

            _uiState.update { state ->
                state.copy(
                    jumpStreak = state.jumpStreak + 1,
                    disabledJumps = state.disabledJumps + jumpId,
                    jumpsClickedSinceLastNonJump = state.jumpsClickedSinceLastNonJump + jumpId,
                    jumpCounts = state.jumpCounts + (jumpId to ((state.jumpCounts[jumpId] ?: 0) + 1)),
                    score = state.score + 3,
                    lastWasNonJump = false
                )
            }
            persistState()
            return
        }

        // Non-jump zone logic
        if (!_uiState.value.hasStarted) return
        if (_uiState.value.lastWasNonJump) return
        if (cellKey in _uiState.value.markedCells) return
        if (_uiState.value.nonJumpMark > 5) return

        _uiState.update { state ->
            val isFifthMark = state.nonJumpMark == 5
            val isSweetSpotFifth = isFifthMark && label == "Sweet Spot"
            val pointsAwarded = if (isSweetSpotFifth) 8 else 1

            state.copy(
                markedCells = state.markedCells + (cellKey to state.nonJumpMark),
                score = state.score + pointsAwarded,
                nonJumpMark = state.nonJumpMark + 1,
                sweetSpotBonus = state.sweetSpotBonus || isSweetSpotFifth,
                jumpStreak = 0,
                disabledJumps = emptySet(),
                jumpsClickedSinceLastNonJump = emptySet(),
                lastWasNonJump = true
            )
        }
        persistState()
    }

    // Import Stubs
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
        setAllRollers(false)
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

            // Log logic here?

            // Persist completed
             _uiState.update { s ->
                val completedParticipant = SevenUpParticipant(
                    handler = active.handler,
                    dog = active.dog,
                    utn = active.utn,
                    jumpSum = jumpSum,
                    nonJumpSum = nonJumpSum,
                    timeRemaining = state.timeRemaining,
                    sweetSpotBonus = state.sweetSpotBonus,
                    allRollers = state.allRollers,
                    heightDivision = active.heightDivision
                )
                s.copy(
                    completed = s.completed + completedParticipant,
                    activeParticipant = s.queue.firstOrNull() ?: SevenUpParticipant("", "", ""),
                    queue = if (s.queue.isEmpty()) emptyList() else s.queue.drop(1)
                )
            }
        }
        resetRoundStateOnly()
        setAllRollers(false)
        logEvent("Next")
        persistState()
    }

    /** Reset only the current round state (score/timer/grid marks) for the current active participant. */
    fun resetCurrentRound() {
        resetRoundStateOnly()
        persistState()
    }

    /** Skip the current participant: move active participant to end of queue, then advance to next in queue. */
    fun skipParticipant() {
        _uiState.update { state ->
            val active = state.activeParticipant
            if (active == null) return@update state

            val newQueue = state.queue + active
            val newActive = newQueue.firstOrNull()
            state.copy(
                activeParticipant = newActive,
                queue = if (newQueue.isEmpty()) emptyList() else newQueue.drop(1)
            )
        }
        resetRoundStateOnly()
        setAllRollers(false)
        logEvent("Skip")
        persistState()
    }

    /** Go to previous participant: pull last queued team to active (if any) and push current active to front of queue. */
    fun previousParticipant() {
        _uiState.update { state ->
            val queue = state.queue
            if (queue.isEmpty()) return@update state

            val prev = queue.last()
            val rest = queue.dropLast(1)
            val newQueue = buildList {
                state.activeParticipant?.let { add(it) }
                addAll(rest)
            }

            state.copy(
                activeParticipant = prev,
                queue = newQueue
            )
        }
        resetRoundStateOnly()
        setAllRollers(false)
        logEvent("Previous")
        persistState()
    }
}

@Serializable
data class SevenUpPendingJsonExport(val filename: String, val content: String)
