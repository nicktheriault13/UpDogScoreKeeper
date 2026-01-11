package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GreedyScreenModel : ScreenModel {

    // NOTE: GreedyParticipant is defined in ExportParticipants.kt (commonMain).

    @Serializable
    data class GreedyPersistedState(
        val activeParticipants: List<GreedyParticipant> = emptyList(),
        val completedParticipants: List<GreedyParticipant> = emptyList()
    )

    data class GreedyStateSnapshot(
        val throwZone: Int,
        val score: Int,
        val misses: Int,
        val rotationDegrees: Int,
        val sweetSpotBonus: Int,
        val activeButtons: Set<String>,
        val activeButtonsByZone: List<Set<String>>,
        val participants: List<GreedyParticipant>,
        val completedParticipants: List<GreedyParticipant>,
        val activeParticipantIndex: Int,
        val isClockwiseDisabled: Boolean,
        val isCounterClockwiseDisabled: Boolean,
        val isRotateStartingZoneVisible: Boolean,
        val allRollersEnabled: Boolean
    )

    // Current State
    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _throwZone = MutableStateFlow(1)
    val throwZone = _throwZone.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees = _rotationDegrees.asStateFlow()

    private val _sweetSpotBonus = MutableStateFlow(0)
    val sweetSpotBonus = _sweetSpotBonus.asStateFlow()

    private val _allRollersEnabled = MutableStateFlow(false)
    val allRollersEnabled = _allRollersEnabled.asStateFlow()

    // Zone rotation button states
    private val _isClockwiseDisabled = MutableStateFlow(false)
    val isClockwiseDisabled = _isClockwiseDisabled.asStateFlow()

    private val _isCounterClockwiseDisabled = MutableStateFlow(false)
    val isCounterClockwiseDisabled = _isCounterClockwiseDisabled.asStateFlow()

    private val _isRotateStartingZoneVisible = MutableStateFlow(true)
    val isRotateStartingZoneVisible = _isRotateStartingZoneVisible.asStateFlow()

    private val _activeButtons = MutableStateFlow<Set<String>>(emptySet())
    val activeButtons = _activeButtons.asStateFlow()

    private val _activeButtonsByZone = MutableStateFlow<List<Set<String>>>(List(5) { emptySet() })
    val activeButtonsByZone = _activeButtonsByZone.asStateFlow()

    private val _participants = MutableStateFlow<List<GreedyParticipant>>(emptyList())
    val participants = _participants.asStateFlow()

    private val _completedParticipants = MutableStateFlow<List<GreedyParticipant>>(emptyList())

    val allParticipants = combine(_completedParticipants, _participants) { completed, active ->
        (completed + active).sortedWith(
            compareByDescending<GreedyParticipant> { it.score }
                .thenByDescending { it.zone4Catches }
                .thenByDescending { it.zone3Catches }
                .thenByDescending { it.zone2Catches }
                .thenByDescending { it.zone1Catches }
                .thenBy { it.numberOfMisses }
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeParticipantIndex = MutableStateFlow(0)
    val activeParticipantIndex = _activeParticipantIndex.asStateFlow()

    // Persistence
    private var dataStore: DataStore? = null
    private val persistenceKey = "GreedyData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val state = Json.decodeFromString<GreedyPersistedState>(json)
                    _participants.value = state.activeParticipants
                    _completedParticipants.value = state.completedParticipants
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun persistState() {
        val store = dataStore ?: return
        val state = GreedyPersistedState(_participants.value, _completedParticipants.value)
        screenModelScope.launch {
            try {
                val json = Json.encodeToString(state)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Undo Stacks
    private val currentRoundUndoStack = ArrayDeque<GreedyStateSnapshot>()
    private val previousRoundsUndoStacks = ArrayDeque<ArrayDeque<GreedyStateSnapshot>>()
    private val MAX_UNDO_LEVELS = 250
    private val MAX_ROUNDS_HISTORY = 200

    private fun pushUndo() {
        val snapshot = GreedyStateSnapshot(
            throwZone = _throwZone.value,
            score = _score.value,
            misses = _misses.value,
            rotationDegrees = _rotationDegrees.value,
            sweetSpotBonus = _sweetSpotBonus.value,
            activeButtons = _activeButtons.value,
            activeButtonsByZone = _activeButtonsByZone.value,
            participants = _participants.value,
            completedParticipants = _completedParticipants.value,
            activeParticipantIndex = _activeParticipantIndex.value,
            isClockwiseDisabled = _isClockwiseDisabled.value,
            isCounterClockwiseDisabled = _isCounterClockwiseDisabled.value,
            isRotateStartingZoneVisible = _isRotateStartingZoneVisible.value,
            allRollersEnabled = _allRollersEnabled.value
        )
        currentRoundUndoStack.addFirst(snapshot)
        if (currentRoundUndoStack.size > MAX_UNDO_LEVELS) {
            currentRoundUndoStack.removeLast()
        }
    }

    fun undo() {
        if (currentRoundUndoStack.isNotEmpty()) {
            restoreSnapshot(currentRoundUndoStack.removeFirst())
        } else if (previousRoundsUndoStacks.isNotEmpty()) {
            val lastRoundStack = previousRoundsUndoStacks.last()
            if (lastRoundStack.isNotEmpty()) {
                restoreSnapshot(lastRoundStack.removeFirst())
                if (lastRoundStack.isEmpty()) {
                    previousRoundsUndoStacks.removeLast()
                }
            }
        }
    }

    private fun restoreSnapshot(snapshot: GreedyStateSnapshot) {
        _throwZone.value = snapshot.throwZone
        _score.value = snapshot.score
        _misses.value = snapshot.misses
        _rotationDegrees.value = snapshot.rotationDegrees
        _sweetSpotBonus.value = snapshot.sweetSpotBonus
        _activeButtons.value = snapshot.activeButtons
        _activeButtonsByZone.value = snapshot.activeButtonsByZone
        _participants.value = snapshot.participants
        _completedParticipants.value = snapshot.completedParticipants
        _activeParticipantIndex.value = snapshot.activeParticipantIndex
        _isClockwiseDisabled.value = snapshot.isClockwiseDisabled
        _isCounterClockwiseDisabled.value = snapshot.isCounterClockwiseDisabled
        _isRotateStartingZoneVisible.value = snapshot.isRotateStartingZoneVisible
        _allRollersEnabled.value = snapshot.allRollersEnabled
    }

    fun handleButtonPress(button: String) {
        if (button in _activeButtons.value) return

        pushUndo()
        val currentZone = _throwZone.value
        if (currentZone in 1..4) {
            // Update active buttons globally for this zone
            _activeButtons.update { it + button }

            // Update active buttons per zone
            _activeButtonsByZone.update { currentList ->
                val mutableList = currentList.toMutableList()
                mutableList[currentZone] = mutableList[currentZone] + button
                mutableList
            }

            if (currentZone == 4 && button == "Sweet Spot") {
                calculateFinalScore()
            }
        }
    }

    fun nextThrowZone(clockwise: Boolean) {
        pushUndo()
        val currentZone = _throwZone.value

        if (currentZone < 4) {
            val nextZone = currentZone + 1
            _throwZone.value = nextZone

            // Logic from React: disable opposite button, hide rotate starting
            if (clockwise) {
                _isCounterClockwiseDisabled.value = true
                _rotationDegrees.value -= 90 // Reversed logic per React snippet
            } else {
                _isClockwiseDisabled.value = true
                _rotationDegrees.value += 90 // Reversed logic per React snippet
            }
            _isRotateStartingZoneVisible.value = false

            // My implementation:
            val nextZoneButtons = _activeButtonsByZone.value.getOrElse(nextZone) { emptySet() }
            _activeButtons.value = nextZoneButtons
        }
    }

    fun rotateStartingZone() {
        pushUndo()
        _rotationDegrees.value += 90
    }

    fun setSweetSpotBonus(bonus: Int) {
        pushUndo()
        if (bonus in 1..8) {
            val actualBonus = if(bonus == 8) 10 else bonus
            _sweetSpotBonus.value = actualBonus
            _score.update { it + actualBonus }
        }
    }

    fun incrementMisses() {
        pushUndo()
        _misses.value++
    }

    fun toggleAllRollers() {
        pushUndo()
        _allRollersEnabled.update { !it }
    }

    private fun calculateFinalScore() {
        var totalScore = 0
        for (zone in 1..4) {
            val zonePoints = when (zone) {
                1 -> 5
                2 -> 4
                3 -> 3
                4 -> 2
                else -> 0
            }
            // Score is based on the number of unique buttons clicked in each zone
            // filtered for actual scoring buttons (X, Y, Z, Sweet Spot)
            val scoringButtons = _activeButtonsByZone.value[zone].filter { it in listOf("X", "Y", "Z", "Sweet Spot") }
            totalScore += scoringButtons.size * zonePoints
        }
        totalScore += _sweetSpotBonus.value
        _score.value = totalScore
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val participant = GreedyParticipant(handler, dog, utn)
        _participants.update {
            if (it.isEmpty()) listOf(participant) else it + participant
        }
        persistState()
    }

    fun nextParticipant() {
        if (_participants.value.isNotEmpty()) {
            // Save current round to previous undo stacks
            if (currentRoundUndoStack.isNotEmpty()) {
                previousRoundsUndoStacks.addLast(ArrayDeque(currentRoundUndoStack))
                if (previousRoundsUndoStacks.size > MAX_ROUNDS_HISTORY) {
                    previousRoundsUndoStacks.removeFirst()
                }
                currentRoundUndoStack.clear()
            }

            // Calculate score for current participant
            // Ensure we use index 0 as current
            val currentParticipant = _participants.value[0]
            val updatedStats = currentParticipant.copy(
                zone1Catches = _activeButtonsByZone.value[1].size,
                zone2Catches = _activeButtonsByZone.value[2].size,
                zone3Catches = _activeButtonsByZone.value[3].size,
                zone4Catches = _activeButtonsByZone.value[4].size,
                finishOnSweetSpot = _activeButtons.value.contains("Sweet Spot") && _throwZone.value == 4,
                sweetSpotBonus = _sweetSpotBonus.value,
                numberOfMisses = _misses.value,
                allRollers = _allRollersEnabled.value,
                score = _score.value
            )

            // Move to completed
            _completedParticipants.update { it + updatedStats }

            // Remove from active queue
            _participants.update { it.drop(1) }
            reset()
            persistState()
        }
    }

    fun skipParticipant() {
        if (_participants.value.isNotEmpty()) {
            val current = _participants.value.first()
            _participants.update { it.drop(1) + current }
            reset()
            persistState()
        }
    }

    fun previousParticipant() {
        // Logic: Move last active back to front (rotation)
        if (_participants.value.isNotEmpty()) {
            val last = _participants.value.last()
            _participants.update {
                listOf(last) + it.dropLast(1)
            }
            reset()
            persistState()
        }
    }

    fun clearParticipants() {
        _participants.value = emptyList()
        _completedParticipants.value = emptyList()
        reset()
        persistState()
    }

    fun selectParticipant(index: Int) {
        if (index in _participants.value.indices && index != 0) {
            _participants.update {
                val mutable = it.toMutableList()
                val selected = mutable.removeAt(index)
                mutable.add(0, selected)
                mutable
            }
            reset()
            persistState()
        }
    }

    // reset function
    fun reset() {
        _score.value = 0
        _throwZone.value = 1
        _misses.value = 0
        _rotationDegrees.value = 0
        _sweetSpotBonus.value = 0
        _activeButtons.value = emptySet()
        _activeButtonsByZone.value = List(5) { emptySet() }
        _isClockwiseDisabled.value = false
        _isCounterClockwiseDisabled.value = false
        _isRotateStartingZoneVisible.value = true
        _allRollersEnabled.value = false
        currentRoundUndoStack.clear()
    }

    fun importParticipantsFromCsv(csv: String) {
        val imported = parseCsv(csv)
        val newParticipants = imported.map { GreedyParticipant(it.handler, it.dog, it.utn) }
        _participants.value = _participants.value + newParticipants // Append
        persistState()
    }

    fun importParticipantsFromXlsx(xlsx: ByteArray) {
        val imported = parseXlsx(xlsx)
        val newParticipants = imported.map { GreedyParticipant(it.handler, it.dog, it.utn) }
        _participants.value = _participants.value + newParticipants
        persistState()
    }

    fun exportParticipantsAsXlsx(templateBytes: ByteArray): ByteArray {
        // Use all participants (completed + active)
        val all = _completedParticipants.value + _participants.value
        return generateGreedyXlsx(all, templateBytes)
    }

    fun exportParticipantsAsCsv(): String {
        val header = "Handler,Dog,UTN,Zone1,Zone2,Zone3,Zone4,FinishSweetSpot,SweetSpotBonus,Misses,AllRollers,Score"
        // Use all participants (completed + active)
        val all = _completedParticipants.value + _participants.value
        val rows = all.map { p ->
            "${p.handler},${p.dog},${p.utn},${p.zone1Catches},${p.zone2Catches},${p.zone3Catches},${p.zone4Catches},${if(p.finishOnSweetSpot) "Y" else "N"},${p.sweetSpotBonus},${p.numberOfMisses},${if(p.allRollers) "Y" else "N"},${p.score}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
