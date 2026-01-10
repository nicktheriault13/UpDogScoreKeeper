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
import kotlin.math.roundToInt

@Serializable
data class TimeWarpRoundResult(
    val score: Int,
    val timeRemaining: Float,
    val misses: Int,
    val zonesCaught: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean
)

@Serializable
data class TimeWarpParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val result: TimeWarpRoundResult? = null
) {
    val displayName: String get() = buildString {
        append(handler.ifBlank { "Unknown Handler" })
        if (dog.isNotBlank()) {
            append(" & ")
            append(dog)
        }
    }
}

@Serializable
data class TimeWarpUiState(
    val score: Int = 0,
    val misses: Int = 0,
    val ob: Int = 0,
    val clickedZones: Set<Int> = emptySet(),
    val sweetSpotClicked: Boolean = false,
    val allRollersClicked: Boolean = false,
    val fieldFlipped: Boolean = false,
    // Timer state in UI state? Or separate?
    // Usually timer is ephemeral, but if we want to persist mid-game state...
    val timeRemaining: Float = 60.0f,
    val isTimerRunning: Boolean = false,
    val activeParticipant: TimeWarpParticipant? = null,
    val queue: List<TimeWarpParticipant> = emptyList(),
    val completed: List<TimeWarpParticipant> = emptyList()
)

class TimeWarpScreenModel : ScreenModel {

    private val _uiState = MutableStateFlow(TimeWarpUiState())

    // Derived Flows
    val score = _uiState.map { it.score }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val misses = _uiState.map { it.misses }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val ob = _uiState.map { it.ob }.stateIn(screenModelScope, SharingStarted.Eagerly, 0)
    val clickedZones = _uiState.map { it.clickedZones }.stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())
    val sweetSpotClicked = _uiState.map { it.sweetSpotClicked }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val allRollersClicked = _uiState.map { it.allRollersClicked }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val fieldFlipped = _uiState.map { it.fieldFlipped }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val timeRemaining = _uiState.map { it.timeRemaining }.stateIn(screenModelScope, SharingStarted.Eagerly, 60.0f)
    val isTimerRunning = _uiState.map { it.isTimerRunning }.stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val activeParticipant = _uiState.map { it.activeParticipant }.stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val participantQueue = _uiState.map { it.queue }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())
    val completedParticipants = _uiState.map { it.completed }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    private var timerJob: Job? = null
    private var dataStore: DataStore? = null
    private val persistenceKey = "TimeWarpData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<TimeWarpUiState>(json)
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

    fun handleZoneClick(zone: Int) {
        if (zone !in _uiState.value.clickedZones) {
            _uiState.update { currentState ->
                currentState.copy(
                    score = currentState.score + 5,
                    clickedZones = currentState.clickedZones + zone
                )
            }
            checkTimePoints()
            persistState()
        }
    }

    private fun checkTimePoints() {
        if (!_uiState.value.clickedZones.contains(3)) {
            // Logic for time points based on remaining time
            // Example points logic
            val bonus = _uiState.value.timeRemaining.roundToInt()
            _uiState.update { currentState ->
                currentState.copy(
                    score = currentState.score + bonus
                )
            }
        }
    }

    fun handleSweetSpotClick() {
        if (_uiState.value.clickedZones.contains(3)) { // Only allow if all 3 zones are clicked
            _uiState.update { currentState ->
                val newScore = if (!currentState.sweetSpotClicked) {
                    currentState.score + 25
                } else {
                    currentState.score - 25
                }
                currentState.copy(
                    score = newScore,
                    sweetSpotClicked = !currentState.sweetSpotClicked
                )
            }
            persistState()
        }
    }

    fun startTimer() {
        if (!_uiState.value.isTimerRunning) {
            _uiState.update { currentState ->
                currentState.copy(
                    isTimerRunning = true,
                    timeRemaining = 60.0f // Reset to 60 seconds on start
                )
            }

            timerJob = screenModelScope.launch {
                var timeLeft = 60.0f
                while (timeLeft > 0f && _uiState.value.isTimerRunning) {
                    delay(1000L)
                    timeLeft -= 1.0f
                    _uiState.update { currentState ->
                        currentState.copy(
                            timeRemaining = timeLeft
                        )
                    }
                }
                if (timeLeft <= 0f) {
                    stopTimer()
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                isTimerRunning = false
            )
        }
        persistState()
    }

    fun setTimeManually(timeStr: String) {
        val newTime = timeStr.toFloatOrNull() ?: return
        timerJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                timeRemaining = newTime,
                isTimerRunning = false
            )
        }
        persistState()
    }

    fun incrementMisses() {
        _uiState.update { currentState ->
            currentState.copy(
                misses = currentState.misses + 1
            )
        }
        persistState()
    }

    fun incrementOb() {
        _uiState.update { currentState ->
            currentState.copy(
                ob = currentState.ob + 1
            )
        }
        persistState()
    }

    fun toggleAllRollers() {
        _uiState.update { currentState ->
            currentState.copy(
                allRollersClicked = !currentState.allRollersClicked
            )
        }
        persistState()
    }

    fun flipField() {
        _uiState.update { currentState ->
            currentState.copy(
                fieldFlipped = !currentState.fieldFlipped
            )
        }
        persistState()
    }

    fun reset() {
        _uiState.value = TimeWarpUiState() // Reset to initial state
        timerJob?.cancel()
        persistState()
    }
}
