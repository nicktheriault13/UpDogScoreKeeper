package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Serializable
data class FunKeyParticipant(
    val handler: String,
    val dog: String,
    val utn: String
)

@Serializable
data class FunKeyRoundData(
    val jump3Sum: Int,
    val jump2Sum: Int,
    val jump1TunnelSum: Int,
    val onePointClicks: Int,
    val twoPointClicks: Int,
    val threePointClicks: Int,
    val fourPointClicks: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean,
    val score: Int,
    val roundTimestamp: String
)

@Serializable
data class FunKeyCompletedParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val score: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean,
    val roundData: FunKeyRoundData
)

enum class FunKeyZoneType { JUMP, KEY }

@Serializable
data class FunKeyUiState(
    val score: Int = 0,
    val misses: Int = 0,
    val sweetSpotOn: Boolean = false,
    val allRollers: Boolean = false,
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
    val completed: List<FunKeyCompletedParticipant> = emptyList()
)

class FunKeyScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    // Undo stack to track state history
    private val undoStack = ArrayDeque<FunKeyUiState>()

    private val _uiState = MutableStateFlow(FunKeyUiState())

    // Derived StateFlows compatible with previous UI consumption
    val score = _uiState.map { it.score }.stateIn(scope, SharingStarted.Eagerly, 0)
    val misses = _uiState.map { it.misses }.stateIn(scope, SharingStarted.Eagerly, 0)
    val sweetSpotOn = _uiState.map { it.sweetSpotOn }.stateIn(scope, SharingStarted.Eagerly, false)
    val allRollers = _uiState.map { it.allRollers }.stateIn(scope, SharingStarted.Eagerly, false)
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
    val completedParticipants = _uiState.map { it.completed }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Per-participant action log (like Greedy/Boom)
    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog = _currentParticipantLog.asStateFlow()

    private fun logEvent(message: String) {
        val entry = "${Clock.System.now()}: $message"
        _currentParticipantLog.value = _currentParticipantLog.value + entry
    }

    // ---- JSON export (triggered on Next Team) ----

    @Serializable
    data class PendingJsonExport(
        val filename: String,
        val content: String
    )

    @Serializable
    private data class FunKeyRoundExport(
        val gameMode: String,
        val exportTimestamp: String,
        val participantData: FunKeyParticipantData,
        val roundResults: FunKeyRoundResults,
        val roundLog: List<String>
    )

    @Serializable
    private data class FunKeyParticipantData(
        val handler: String,
        val dog: String,
        val utn: String,
        val completedAt: String
    )

    @Serializable
    private data class FunKeyRoundResults(
        val jump3Sum: Int,
        val jump2Sum: Int,
        val jump1TunnelSum: Int,
        val onePointClicks: Int,
        val twoPointClicks: Int,
        val threePointClicks: Int,
        val fourPointClicks: Int,
        val sweetSpot: String,
        val allRollers: String,
        val totalScore: Int
    )

    // JSON export infrastructure
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<PendingJsonExport?> = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    private fun safeNamePart(input: String, fallback: String): String {
        val cleaned = input.trim().replace(Regex("[^a-zA-Z0-9]"), "")
        return if (cleaned.isEmpty()) fallback else cleaned
    }

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
        pushUndo()
        _uiState.update { state ->
            val enabling = !state.sweetSpotOn
            logEvent("Sweet Spot ${if (enabling) "activated" else "deactivated"}")
            val delta = if (enabling) 2 else -2
            state.copy(
                sweetSpotOn = enabling,
                score = (state.score + delta).coerceAtLeast(0)
            )
        }
        persistState()
    }

    fun toggleAllRollers() {
        pushUndo()
        val newState = !_uiState.value.allRollers
        logEvent("All Rollers ${if (newState) "enabled" else "disabled"}")
        _uiState.update { state ->
            state.copy(allRollers = newState)
        }
        persistState()
    }

    fun incrementMisses() {
        pushUndo()
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
        pushUndo()
        logEvent("Button pressed: $zone")
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

        // Scoring rules:
        // Jump3 = +3, Jump2 = +2, Jump1 = +1, Tunnel = +1
        val (delta, updated) = when (zone) {
            "JUMP3" -> 3 to state.copy(jump3Count = state.jump3Count + 1)
            "JUMP3B" -> 3 to state.copy(jump3bCount = state.jump3bCount + 1)
            "JUMP2" -> 2 to state.copy(jump2Count = state.jump2Count + 1)
            "JUMP2B" -> 2 to state.copy(jump2bCount = state.jump2bCount + 1)
            "JUMP1" -> 1 to state.copy(jump1Count = state.jump1Count + 1)
            "TUNNEL" -> 1 to state.copy(tunnelCount = state.tunnelCount + 1)
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
        undoStack.clear()
        _uiState.update {
            it.copy(
                score = 0,
                misses = 0,
                sweetSpotOn = false,
                allRollers = false,
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
        // Clear the participant log when resetting
        _currentParticipantLog.value = emptyList()
        persistState()
    }

    private fun pushUndo() {
        // Save current state to undo stack
        undoStack.add(_uiState.value)
        // Limit stack size to prevent memory issues
        if (undoStack.size > 200) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) {
            return
        }

        // Restore the last saved state
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        _uiState.value = previousState
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

            // Create round data from current state
            val jump3Sum = state.jump3Count + state.jump3bCount
            val jump2Sum = state.jump2Count + state.jump2bCount
            val jump1TunnelSum = state.jump1Count + state.tunnelCount

            val roundData = FunKeyRoundData(
                jump3Sum = jump3Sum,
                jump2Sum = jump2Sum,
                jump1TunnelSum = jump1TunnelSum,
                onePointClicks = state.key1Count,
                twoPointClicks = state.key2Count,
                threePointClicks = state.key3Count,
                fourPointClicks = state.key4Count,
                sweetSpot = state.sweetSpotOn,
                allRollers = state.allRollers,
                score = state.score,
                roundTimestamp = kotlinx.datetime.Clock.System.now().toString()
            )

            // Create completed participant with round data
            val completedParticipant = FunKeyCompletedParticipant(
                handler = active.handler,
                dog = active.dog,
                utn = active.utn,
                score = state.score,
                sweetSpot = state.sweetSpotOn,
                allRollers = state.allRollers,
                roundData = roundData
            )

            // Emit JSON export for this participant
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            fun pad(n: Int) = n.toString().padStart(2, '0')
            val stamp = buildString {
                append(now.year)
                append(pad(now.monthNumber))
                append(pad(now.dayOfMonth))
                append('_')
                append(pad(now.hour))
                append(pad(now.minute))
                append(pad(now.second))
            }
            val filename = "FunKey_${safeNamePart(active.handler, "Handler")}_${safeNamePart(active.dog, "Dog")}_${stamp}.json"

            val exportData = FunKeyRoundExport(
                gameMode = "Fun Key",
                exportTimestamp = Clock.System.now().toString(),
                participantData = FunKeyParticipantData(
                    handler = active.handler,
                    dog = active.dog,
                    utn = active.utn,
                    completedAt = now.toString()
                ),
                roundResults = FunKeyRoundResults(
                    jump3Sum = jump3Sum,
                    jump2Sum = jump2Sum,
                    jump1TunnelSum = jump1TunnelSum,
                    onePointClicks = state.key1Count,
                    twoPointClicks = state.key2Count,
                    threePointClicks = state.key3Count,
                    fourPointClicks = state.key4Count,
                    sweetSpot = if (state.sweetSpotOn) "Yes" else "No",
                    allRollers = if (state.allRollers) "Yes" else "No",
                    totalScore = state.score
                ),
                roundLog = _currentParticipantLog.value
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )

            val completed = state.completed + completedParticipant
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
                allRollers = false,
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
        // Clear the participant log for the next participant
        _currentParticipantLog.value = emptyList()
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
                allRollers = false,
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

    enum class ImportMode { ReplaceAll, Add }

    private fun applyImportedParticipants(players: List<FunKeyParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return
        _uiState.update { s ->
            when (mode) {
                ImportMode.ReplaceAll -> s.copy(
                    activeParticipant = players.first(),
                    queue = players.drop(1),
                    completed = emptyList()
                )
                ImportMode.Add -> {
                    if (s.activeParticipant == null) {
                        s.copy(activeParticipant = players.first(), queue = players.drop(1))
                    } else {
                        s.copy(queue = s.queue + players)
                    }
                }
            }
        }
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseCsv(csvText)
        val participants = imported.map { FunKeyParticipant(it.handler, it.dog, it.utn) }
        applyImportedParticipants(participants, mode)
    }

    fun importParticipantsFromXlsx(bytes: ByteArray, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseXlsx(bytes)
        val participants = imported.map { FunKeyParticipant(it.handler, it.dog, it.utn) }
        applyImportedParticipants(participants, mode)
    }

    private fun checkPhaseTransition() {
        // No-op for now.
    }
}
