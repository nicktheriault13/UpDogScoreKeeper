package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FrizgilityScreenModel : ScreenModel {

    private val undoStack = ArrayDeque<FrizgilityUiState>()
    private var timerJob: Job? = null
    private var logCounter = 0

    private val _uiState = MutableStateFlow(FrizgilityUiState())
    val uiState = _uiState.asStateFlow()

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning = _timerRunning.asStateFlow()
    private val _timeLeft = MutableStateFlow(DEFAULT_TIMER_SECONDS)
    val timeLeft = _timeLeft.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries = _logEntries.asStateFlow()

    fun toggleSweetSpot() {
        pushUndo()
        _uiState.update { state ->
            state.copy(sweetSpotActive = !state.sweetSpotActive)
        }
        appendLog("Sweet Spot toggled")
    }

    fun toggleAllRollers() {
        pushUndo()
        _uiState.update { state -> state.copy(allRollersEnabled = !state.allRollersEnabled) }
        appendLog("All Rollers toggled")
    }

    fun handleObstacleClick(lane: Int) {
        _uiState.update { state ->
            if (!state.obstaclePhaseActive || lane in state.laneLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (lane) {
                1 -> state.counters.copy(obstacle1 = state.counters.obstacle1 + 1)
                2 -> state.counters.copy(obstacle2 = state.counters.obstacle2 + 1)
                3 -> state.counters.copy(obstacle3 = state.counters.obstacle3 + 1)
                else -> state.counters
            }
            val newLocks = state.laneLocks + lane
            val nextState = state.copy(
                counters = updatedCounters,
                laneLocks = newLocks
            )
            if (newLocks.size == 3) {
                nextState.copy(
                    obstaclePhaseActive = false,
                    catchPhaseActive = true,
                    missClicksInPhase = 0
                )
            } else {
                nextState
            }
        }
        appendLog("Obstacle lane $lane pressed")
    }

    fun handleFailClick(lane: Int) {
        _uiState.update { state ->
            if (!state.obstaclePhaseActive || lane in state.laneLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (lane) {
                1 -> state.counters.copy(fail1 = state.counters.fail1 + 1)
                2 -> state.counters.copy(fail2 = state.counters.fail2 + 1)
                3 -> state.counters.copy(fail3 = state.counters.fail3 + 1)
                else -> state.counters
            }
            val newLocks = state.laneLocks + lane
            val nextState = state.copy(
                counters = updatedCounters,
                laneLocks = newLocks
            )
            if (newLocks.size == 3) {
                nextState.copy(
                    obstaclePhaseActive = false,
                    catchPhaseActive = true,
                    missClicksInPhase = 0
                )
            } else {
                nextState
            }
        }
        appendLog("Fail lane $lane pressed")
    }

    fun handleCatchClick(points: Int) {
        _uiState.update { state ->
            if (!state.catchPhaseActive || points in state.catchLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (points) {
                3 -> state.counters.copy(catch3to10 = state.counters.catch3to10 + 1)
                10 -> state.counters.copy(catch10plus = state.counters.catch10plus + 1)
                else -> state.counters
            }
            val updatedState = state.copy(
                counters = updatedCounters,
                catchLocks = state.catchLocks + points
            )
            updatedState.resetRound()
        }
        appendLog("Catch button ${if (points == 3) "3-10" else "10+"} pressed")
    }

    fun handleMissClick() {
        _uiState.update { state ->
            if (state.missClicksInPhase >= 3) return@update state
            pushUndoInternal()
            val updatedCounters = state.counters.copy(miss = state.counters.miss + 1)
            val newMissClicks = state.missClicksInPhase + 1
            val afterMiss = state.copy(
                counters = updatedCounters,
                missClicksInPhase = newMissClicks
            )
            when {
                newMissClicks >= 3 -> afterMiss.resetRound()
                state.catchPhaseActive && newMissClicks == 1 -> afterMiss.copy(
                    obstaclePhaseActive = true,
                    laneLocks = emptySet()
                )
                else -> afterMiss
            }
        }
        appendLog("Miss button pressed")
    }

    fun resetGame() {
        pushUndo()
        _uiState.update { state ->
            state.copy(
                counters = FrizgilityCounters(),
                laneLocks = emptySet(),
                catchLocks = emptySet(),
                obstaclePhaseActive = true,
                catchPhaseActive = false,
                missClicksInPhase = 0,
                sweetSpotActive = false
            )
        }
        appendLog("Game reset")
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        if (handler.isBlank() || dog.isBlank()) return
        _uiState.update { state ->
            state.copy(
                participants = state.participants + FrizgilityParticipant(handler.trim(), dog.trim(), utn.trim())
            )
        }
        appendLog("Participant added: $handler & $dog")
    }

    fun skipParticipant() {
        _uiState.update { state ->
            val current = state.activeParticipant ?: return@update state
            if (state.participants.size <= 1) return@update state
            val mutable = state.participants.toMutableList()
            mutable.removeAt(state.activeParticipantIndex)
            mutable.add(current)
            state.copy(participants = mutable, activeParticipantIndex = 0)
        }
        appendLog("Participant skipped")
    }

    fun nextParticipant() {
        _uiState.update { state ->
            if (state.participants.isEmpty()) return@update state.resetRound()
            val mutable = state.participants.toMutableList()
            if (state.activeParticipantIndex in mutable.indices) {
                mutable.removeAt(state.activeParticipantIndex)
            }
            val nextState = state.copy(
                participants = mutable,
                activeParticipantIndex = 0
            ).resetRound()
            nextState
        }
        appendLog("Advanced to next participant")
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeFirst()
        _uiState.value = previous
        appendLog("Undo applied")
    }

    fun startTimer(durationSeconds: Int = DEFAULT_TIMER_SECONDS) {
        if (_timerRunning.value) return
        _timeLeft.value = durationSeconds
        _timerRunning.value = true
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value = max(0, _timeLeft.value - 1)
            }
            _timerRunning.value = false
        }
        appendLog("Timer started")
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        appendLog("Timer stopped")
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = DEFAULT_TIMER_SECONDS
    }

    fun exportParticipantsSnapshot(): String {
        val header = "Handler,Dog,UTN,Score"
        val rows = uiState.value.participants.map { participant ->
            listOf(participant.handler, participant.dog, participant.utn, uiState.value.scoreBreakdown.totalScore.toString())
                .joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun importParticipantsFromCsv(csv: String) {
        val rows = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rows.isEmpty()) return
        val participants = rows.mapNotNull { line ->
            val parts = line.split(',')
            if (parts.isEmpty()) return@mapNotNull null
            val handler = parts.getOrNull(0)?.trim().orEmpty()
            val dog = parts.getOrNull(1)?.trim().orEmpty()
            val utn = parts.getOrNull(2)?.trim().orEmpty()
            if (handler.isBlank() && dog.isBlank()) return@mapNotNull null
            FrizgilityParticipant(handler, dog, utn)
        }
        if (participants.isEmpty()) return
        _uiState.update { state ->
            state.copy(participants = participants, activeParticipantIndex = 0)
                .resetRound()
        }
        appendLog("Imported ${participants.size} participants")
    }

    fun clearParticipants() {
        _uiState.update { it.copy(participants = emptyList(), activeParticipantIndex = 0) }
        appendLog("Participants cleared")
    }

    fun resetLog() {
        _logEntries.value = emptyList()
    }

    private fun FrizgilityUiState.resetRound(): FrizgilityUiState = copy(
        counters = counters,
        laneLocks = emptySet(),
        catchLocks = emptySet(),
        obstaclePhaseActive = true,
        catchPhaseActive = false,
        missClicksInPhase = 0
    ).clearCountsIfNeeded()

    private fun FrizgilityUiState.clearCountsIfNeeded(): FrizgilityUiState = copy(
        counters = counters.copy(),
        sweetSpotActive = sweetSpotActive
    )

    private fun pushUndo() {
        undoStack.addFirst(_uiState.value)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeLast()
        }
    }

    private fun pushUndoInternal() {
        undoStack.addFirst(_uiState.value)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeLast()
        }
    }

    private fun appendLog(message: String) {
        logCounter += 1
        val entry = "[${logCounter.toString().padStart(3, '0')}] $message"
        _logEntries.update { listOf(entry) + it }
    }

    companion object {
        private const val DEFAULT_TIMER_SECONDS = 60
        private const val MAX_UNDO_SIZE = 200
    }
}

data class FrizgilityParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
)

data class FrizgilityCounters(
    val obstacle1: Int = 0,
    val obstacle2: Int = 0,
    val obstacle3: Int = 0,
    val fail1: Int = 0,
    val fail2: Int = 0,
    val fail3: Int = 0,
    val catch3to10: Int = 0,
    val catch10plus: Int = 0,
    val miss: Int = 0
)

data class ScoreBreakdown(
    val obstacle1: Int,
    val obstacle2: Int,
    val obstacle3: Int,
    val fail1: Int,
    val fail2: Int,
    val fail3: Int,
    val catch3to10: Int,
    val catch10plus: Int,
    val miss: Int,
    val sweetSpotActive: Boolean,
    val totalScore: Int
)

data class FrizgilityUiState(
    val counters: FrizgilityCounters = FrizgilityCounters(),
    val sweetSpotActive: Boolean = false,
    val allRollersEnabled: Boolean = false,
    val obstaclePhaseActive: Boolean = true,
    val catchPhaseActive: Boolean = false,
    val laneLocks: Set<Int> = emptySet(),
    val catchLocks: Set<Int> = emptySet(),
    val missClicksInPhase: Int = 0,
    val sidebarCollapsed: Boolean = false,
    val participants: List<FrizgilityParticipant> = listOf(
        FrizgilityParticipant("Alex", "Nova", "UTN-001"),
        FrizgilityParticipant("Blair", "Zelda", "UTN-002"),
        FrizgilityParticipant("Casey", "Milo", "UTN-003")
    ),
    val activeParticipantIndex: Int = 0
) {
    val scoreBreakdown: ScoreBreakdown = counters.toScoreBreakdown(sweetSpotActive)
    val activeParticipant: FrizgilityParticipant?
        get() = participants.getOrNull(activeParticipantIndex)
    val queue: List<FrizgilityParticipant>
        get() = if (participants.size <= 1) emptyList() else participants.drop(activeParticipantIndex + 1)
}

private fun FrizgilityCounters.toScoreBreakdown(sweetSpotActive: Boolean): ScoreBreakdown {
    val obstacleScore = (obstacle1 + obstacle2 + obstacle3) * 5
    val catchScore = (catch3to10 * 3) + (catch10plus * 10)
    val sweetSpotScore = if (sweetSpotActive) 10 else 0
    val total = obstacleScore + catchScore + sweetSpotScore
    return ScoreBreakdown(
        obstacle1,
        obstacle2,
        obstacle3,
        fail1,
        fail2,
        fail3,
        catch3to10,
        catch10plus,
        miss,
        sweetSpotActive,
        total
    )
}
