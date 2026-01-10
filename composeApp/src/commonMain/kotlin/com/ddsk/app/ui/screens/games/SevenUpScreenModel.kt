package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.time.TimeSource

@Serializable
data class SevenUpParticipant(
    val handler: String,
    val dog: String,
    val utn: String
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
    val completed: List<SevenUpParticipant> = emptyList() // Needs more robust result structure if stats needed on completed
)

class SevenUpScreenModel : ScreenModel {

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
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val players = imported.map { SevenUpParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { SevenUpParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    private fun applyImportedPlayers(players: List<SevenUpParticipant>) {
        if (players.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                queue = players.drop(1),
                activeParticipant = players.first()
            )
        }
        reset()
    }
}
