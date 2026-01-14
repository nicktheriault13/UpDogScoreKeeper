package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

@Serializable
data class FunKeyParticipant(
    val handler: String,
    val dog: String,
    val utn: String
)

enum class FunKeyZoneType { JUMP, KEY }

@Serializable
data class FunKeyUiState(
    val score: Int = 0,
    val misses: Int = 0,
    val sweetSpotOn: Boolean = false,
    val activatedKeys: Set<String> = emptySet(),
    val isPurpleEnabled: Boolean = true,
    val isBlueEnabled: Boolean = false,
    val jump1Count: Int = 0,
    val jump2Count: Int = 0,
    val jump3Count: Int = 0,
    val jump2bCount: Int = 0,
    val jump3bCount: Int = 0,
    val tunnelCount: Int = 0,
    val key1Count: Int = 0,
    val key2Count: Int = 0,
    val key3Count: Int = 0,
    val key4Count: Int = 0,
    val activeParticipant: FunKeyParticipant? = null,
    val queue: List<FunKeyParticipant> = emptyList(),
    val completed: List<FunKeyParticipant> = emptyList()
)

class FunKeyScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    private val _uiState = MutableStateFlow(FunKeyUiState())

    // Derived StateFlows compatible with previous UI consumption
    val score = _uiState.map { it.score }.stateIn(scope, SharingStarted.Eagerly, 0)
    val misses = _uiState.map { it.misses }.stateIn(scope, SharingStarted.Eagerly, 0)
    val sweetSpotOn = _uiState.map { it.sweetSpotOn }.stateIn(scope, SharingStarted.Eagerly, false)
    val activatedKeys = _uiState.map { it.activatedKeys }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val isPurpleEnabled = _uiState.map { it.isPurpleEnabled }.stateIn(scope, SharingStarted.Eagerly, true)
    val isBlueEnabled = _uiState.map { it.isBlueEnabled }.stateIn(scope, SharingStarted.Eagerly, false)

    val jump1Count = _uiState.map { it.jump1Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val jump2Count = _uiState.map { it.jump2Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val jump3Count = _uiState.map { it.jump3Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val jump2bCount = _uiState.map { it.jump2bCount }.stateIn(scope, SharingStarted.Eagerly, 0)
    val jump3bCount = _uiState.map { it.jump3bCount }.stateIn(scope, SharingStarted.Eagerly, 0)
    val tunnelCount = _uiState.map { it.tunnelCount }.stateIn(scope, SharingStarted.Eagerly, 0)

    val key1Count = _uiState.map { it.key1Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val key2Count = _uiState.map { it.key2Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val key3Count = _uiState.map { it.key3Count }.stateIn(scope, SharingStarted.Eagerly, 0)
    val key4Count = _uiState.map { it.key4Count }.stateIn(scope, SharingStarted.Eagerly, 0)

    val activeParticipant = _uiState.map { it.activeParticipant }.stateIn(scope, SharingStarted.Eagerly, null)
    val participantQueue = _uiState.map { it.queue }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var dataStore: DataStore? = null
    private val persistenceKey = "FunKeyData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<FunKeyUiState>(json)
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

    fun toggleSweetSpot() {
        _uiState.update { it.copy(sweetSpotOn = !it.sweetSpotOn) }
        persistState()
    }

    fun incrementMisses() {
        _uiState.update { it.copy(misses = it.misses + 1) }
        persistState()
    }

    fun handleCatch(type: FunKeyZoneType, points: Int, zone: String) {
        // Track key activations as a simple set of zone IDs
        _uiState.update { state ->
            val activated = if (type == FunKeyZoneType.KEY) state.activatedKeys + zone else state.activatedKeys
            state.copy(score = state.score + points, activatedKeys = activated)
        }
        persistState()
    }

    // Keeping old helper methods for any other UI calls
    fun handleJump(id: String) {
        if (!isPurpleEnabled.value) return
        _uiState.update { state ->
            val points = 2
            val newScore = state.score + points
            when (id) {
                "J1" -> state.copy(score = newScore, jump1Count = state.jump1Count + 1)
                "J2" -> state.copy(score = newScore, jump2Count = state.jump2Count + 1)
                "J3" -> state.copy(score = newScore, jump3Count = state.jump3Count + 1)
                "J2b" -> state.copy(score = newScore, jump2bCount = state.jump2bCount + 1)
                "J3b" -> state.copy(score = newScore, jump3bCount = state.jump3bCount + 1)
                "T" -> state.copy(score = newScore, tunnelCount = state.tunnelCount + 1)
                else -> state
            }
        }
        persistState()
    }

    fun handleKey(id: String) {
        if (!isBlueEnabled.value) return
        _uiState.update { state ->
            val points = 5
            val newScore = state.score + points
            val activated = state.activatedKeys + id
            when (id) {
                "K1" -> state.copy(score = newScore, key1Count = state.key1Count + 1, activatedKeys = activated)
                "K2" -> state.copy(score = newScore, key2Count = state.key2Count + 1, activatedKeys = activated)
                "K3" -> state.copy(score = newScore, key3Count = state.key3Count + 1, activatedKeys = activated)
                "K4" -> state.copy(score = newScore, key4Count = state.key4Count + 1, activatedKeys = activated)
                else -> state.copy(score = newScore, activatedKeys = activated)
            }
        }
        persistState()
    }

    fun reset() {
        _uiState.update { it.copy(score = 0, misses = 0, sweetSpotOn = false, activatedKeys = emptySet()) }
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val p = FunKeyParticipant(handler, dog, utn)
        _uiState.update { currentState ->
            if (currentState.activeParticipant == null) {
                currentState.copy(activeParticipant = p)
            } else {
                currentState.copy(queue = currentState.queue + p)
            }
        }
        persistState()
    }

    fun nextParticipant() {
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            val completed = state.completed + active
            val next = state.queue.firstOrNull()
            val remaining = if (state.queue.isNotEmpty()) state.queue.drop(1) else emptyList()
            state.copy(activeParticipant = next, queue = remaining, completed = completed, score = 0, misses = 0, sweetSpotOn = false, activatedKeys = emptySet())
        }
        persistState()
    }

    fun skipParticipant() {
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            val next = state.queue.firstOrNull()
            val remaining = if (state.queue.isNotEmpty()) state.queue.drop(1) else emptyList()
            state.copy(activeParticipant = next, queue = remaining + active, score = 0, misses = 0, sweetSpotOn = false, activatedKeys = emptySet())
        }
        persistState()
    }

    fun clearParticipants() {
        _uiState.value = FunKeyUiState()
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val participants = imported.map { FunKeyParticipant(it.handler, it.dog, it.utn) }
        if (participants.isEmpty()) return
        _uiState.value = _uiState.value.copy(activeParticipant = participants.first(), queue = participants.drop(1))
        persistState()
    }

    fun importParticipantsFromXlsx(bytes: ByteArray) {
        val imported = parseXlsx(bytes)
        val participants = imported.map { FunKeyParticipant(it.handler, it.dog, it.utn) }
        if (participants.isEmpty()) return
        _uiState.value = _uiState.value.copy(activeParticipant = participants.first(), queue = participants.drop(1))
        persistState()
    }

    private fun checkPhaseTransition() {
        // No-op for now.
    }
}
