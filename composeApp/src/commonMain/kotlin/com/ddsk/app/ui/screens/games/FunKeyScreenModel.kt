package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
        // React behavior: toggling Sweet Spot immediately adjusts score (+2/-2)
        _uiState.update { state ->
            val enabling = !state.sweetSpotOn
            val delta = if (enabling) 2 else -2
            state.copy(
                sweetSpotOn = enabling,
                score = (state.score + delta).coerceAtLeast(0)
            )
        }
        persistState()
    }

    fun incrementMisses() {
        _uiState.update { it.copy(misses = it.misses + 1) }
        persistState()
    }

    /**
     * New unified scoring entrypoint used by the Compose UI.
     *
     * Matches the React grid behavior:
     * - "Purple" phase: jump/tunnel buttons are enabled.
     * - After any jump/tunnel click: switch to "Blue" phase.
     * - "Blue" phase: key buttons are enabled (one-shot; turn green and disable).
     * - After any key click: switch back to "Purple" phase.
     */
    fun handleCatch(type: FunKeyZoneType, @Suppress("UNUSED_PARAMETER") points: Int, zone: String) {
        _uiState.update { state ->
             when (type) {
                 FunKeyZoneType.JUMP -> handleJumpCatchInternal(state, zone)
                 FunKeyZoneType.KEY -> handleKeyCatchInternal(state, zone)
             }
         }
         persistState()
    }

    private fun handleJumpCatchInternal(state: FunKeyUiState, zone: String): FunKeyUiState {
        if (!state.isPurpleEnabled) return state

        // React scoring rules:
        // Jump3 = +3, Jump2 = +2, Jump1 = +0 (count only), Tunnel = +0 (count only)
        val (delta, updated) = when (zone) {
            "JUMP3" -> 3 to state.copy(jump3Count = state.jump3Count + 1)
            "JUMP3B" -> 3 to state.copy(jump3bCount = state.jump3bCount + 1)
            "JUMP2" -> 2 to state.copy(jump2Count = state.jump2Count + 1)
            "JUMP2B" -> 2 to state.copy(jump2bCount = state.jump2bCount + 1)
            "JUMP1" -> 0 to state.copy(jump1Count = state.jump1Count + 1)
            "TUNNEL" -> 0 to state.copy(tunnelCount = state.tunnelCount + 1)
            else -> 0 to state
        }

        // Phase transition: purple -> blue after a jump/tunnel
        return updated.copy(
            score = updated.score + delta,
            isPurpleEnabled = false,
            isBlueEnabled = true
        )
    }

    private fun handleKeyCatchInternal(state: FunKeyUiState, zone: String): FunKeyUiState {
        if (!state.isBlueEnabled) return state
        if (zone in state.activatedKeys) return state // one-shot keys

        val (points, updated) = when (zone) {
            "KEY1" -> 1 to state.copy(key1Count = state.key1Count + 1)
            "KEY2" -> 2 to state.copy(key2Count = state.key2Count + 1)
            "KEY3" -> 3 to state.copy(key3Count = state.key3Count + 1)
            "KEY4" -> 4 to state.copy(key4Count = state.key4Count + 1)
            else -> 0 to state
        }

        // Add the current key to activated keys
        val newActivatedKeys = updated.activatedKeys + zone

        // Check if all 4 keys are now activated - if so, reset them all
        val allKeysActivated = newActivatedKeys.containsAll(setOf("KEY1", "KEY2", "KEY3", "KEY4"))
        val finalActivatedKeys = if (allKeysActivated) emptySet() else newActivatedKeys

        // Phase transition: blue -> purple after a key
        return updated.copy(
            score = updated.score + points,
            activatedKeys = finalActivatedKeys,
            isBlueEnabled = false,
            isPurpleEnabled = true
        )
    }

    // Keeping old helper methods for any other UI calls
    fun handleJump(id: String) {
        // Legacy IDs; keep behavior consistent with new rules.
        val mappedZone = when (id) {
            "J1" -> "JUMP1"
            "J2" -> "JUMP2"
            "J3" -> "JUMP3"
            "J2b" -> "JUMP2B"
            "J3b" -> "JUMP3B"
            "T" -> "TUNNEL"
            else -> id
        }
        handleCatch(FunKeyZoneType.JUMP, 0, mappedZone)
    }

    fun handleKey(id: String) {
        // Legacy IDs; keep behavior consistent with new rules.
        val mappedZone = when (id) {
            "K1" -> "KEY1"
            "K2" -> "KEY2"
            "K3" -> "KEY3"
            "K4" -> "KEY4"
            else -> id
        }
        handleCatch(FunKeyZoneType.KEY, 0, mappedZone)
    }

    fun reset() {
        // React resetScore resets all per-run scoring state + gating
        _uiState.update {
            it.copy(
                score = 0,
                misses = 0,
                sweetSpotOn = false,
                activatedKeys = emptySet(),
                isPurpleEnabled = true,
                isBlueEnabled = false,
                jump1Count = 0,
                jump2Count = 0,
                jump3Count = 0,
                jump2bCount = 0,
                jump3bCount = 0,
                tunnelCount = 0,
                key1Count = 0,
                key2Count = 0,
                key3Count = 0,
                key4Count = 0
            )
        }
        persistState()
    }

    fun undo() {
        // Simple undo: reset the current round state but keep participants
        _uiState.update {
            it.copy(
                score = 0,
                sweetSpotOn = false,
                activatedKeys = emptySet(),
                isPurpleEnabled = true,
                isBlueEnabled = false,
                jump1Count = 0,
                jump2Count = 0,
                jump3Count = 0,
                jump2bCount = 0,
                jump3bCount = 0,
                tunnelCount = 0,
                key1Count = 0,
                key2Count = 0,
                key3Count = 0,
                key4Count = 0
            )
        }
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
            // Move to next team and clear per-run state
            state.copy(
                activeParticipant = next,
                queue = remaining,
                completed = completed,
                score = 0,
                misses = 0,
                sweetSpotOn = false,
                activatedKeys = emptySet(),
                isPurpleEnabled = true,
                isBlueEnabled = false,
                jump1Count = 0,
                jump2Count = 0,
                jump3Count = 0,
                jump2bCount = 0,
                jump3bCount = 0,
                tunnelCount = 0,
                key1Count = 0,
                key2Count = 0,
                key3Count = 0,
                key4Count = 0
            )
        }
        persistState()
    }

    fun skipParticipant() {
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            val next = state.queue.firstOrNull()
            val remaining = if (state.queue.isNotEmpty()) state.queue.drop(1) else emptyList()
            // Skip: send current active to back of queue and clear per-run state
            state.copy(
                activeParticipant = next,
                queue = remaining + active,
                score = 0,
                misses = 0,
                sweetSpotOn = false,
                activatedKeys = emptySet(),
                isPurpleEnabled = true,
                isBlueEnabled = false,
                jump1Count = 0,
                jump2Count = 0,
                jump3Count = 0,
                jump2bCount = 0,
                jump3bCount = 0,
                tunnelCount = 0,
                key1Count = 0,
                key2Count = 0,
                key3Count = 0,
                key4Count = 0
            )
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
