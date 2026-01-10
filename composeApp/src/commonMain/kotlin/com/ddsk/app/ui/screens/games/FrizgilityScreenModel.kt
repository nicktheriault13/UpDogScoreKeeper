package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    private var dataStore: DataStore? = null
    private val persistenceKey = "FrizgilityData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<FrizgilityUiState>(json)
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

    fun toggleSweetSpot() {
        pushUndo()
        _uiState.update { state ->
            state.copy(sweetSpotActive = !state.sweetSpotActive)
        }
        appendLog("Sweet Spot toggled")
        persistState()
    }

    fun toggleAllRollers() {
        pushUndo()
        _uiState.update { state -> state.copy(allRollersEnabled = !state.allRollersEnabled) }
        appendLog("All Rollers toggled")
        persistState()
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
        persistState()
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
        persistState()
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
        persistState()
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
        persistState()
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
        _timeLeft.value = DEFAULT_TIMER_SECONDS
        stopTimer()
        appendLog("Game reset")
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val players = imported.map { FrizgilityParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { FrizgilityParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    private fun applyImportedPlayers(players: List<FrizgilityParticipant>) {
        if (players.isEmpty()) return
        _uiState.update {
            it.copy(
                participants = players,
                activeParticipantIndex = 0
            )
        }
        resetGame()
        appendLog("Imported ${players.size} participants")
        persistState()
    }

    fun exportParticipantsSnapshot(): String {
        // ... implementation existing or placeholder ...
        // Implementing basic CSV format return
        val participants = _uiState.value.participants.toMutableList()
        _uiState.value.activeParticipant?.let { participants.add(0, it) }
        return participants.joinToString("\n") { "${it.handler},${it.dog},${it.utn}" }
    }

    fun nextParticipant() {
        if (_uiState.value.participants.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    activeParticipantIndex = (it.activeParticipantIndex + 1) % it.participants.size
                )
            }
            resetGame()
            persistState()
        }
    }

    fun skipParticipant() {
        nextParticipant() // For now skip just moves next
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val newParticipant = FrizgilityParticipant(handler, dog, utn)
        _uiState.update { it.copy(participants = it.participants + newParticipant) }
        persistState()
    }

    fun clearParticipants() {
        _uiState.update { it.copy(participants = emptyList(), activeParticipantIndex = 0) }
        persistState()
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
        persistState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeFirst()
        _uiState.value = previous
        appendLog("Undo applied")
        persistState()
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
        // Note: logs are currently not persisted in state, but could be
    }

    companion object {
        private const val DEFAULT_TIMER_SECONDS = 60
        private const val MAX_UNDO_SIZE = 200
    }
}

@Serializable
data class FrizgilityParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
)

@Serializable
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

@Serializable
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

@Serializable
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
    val scoreBreakdown: ScoreBreakdown get() = counters.toScoreBreakdown(sweetSpotActive)
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
