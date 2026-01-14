package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GreedyScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

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
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeParticipantIndex = MutableStateFlow(0)
    val activeParticipantIndex = _activeParticipantIndex.asStateFlow()

    // Persistence
    private var dataStore: DataStore? = null
    private val persistenceKey = "GreedyData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        // Desktop builds don't provide Dispatchers.Main by default.
        scope.launch {
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
        scope.launch {
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

    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog: StateFlow<List<String>> = _currentParticipantLog.asStateFlow()

    private fun logEvent(message: String) {
        val participant = _participants.value.firstOrNull()
        val who = participant?.handler?.takeIf { it.isNotBlank() } ?: "Unknown"
        val entry = "${Clock.System.now()}: $message"
        _currentParticipantLog.value = _currentParticipantLog.value + entry
        // keep existing global log behavior if any
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
        logEvent("Button pressed: $button")
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
        logEvent(if (clockwise) "Next ThrowZone (clockwise)" else "Next ThrowZone (counterclockwise)")
    }

    fun rotateStartingZone() {
        pushUndo()
        _rotationDegrees.value += 90
        logEvent("Rotate Starting Zone")
    }

    enum class ImportMode { Add, ReplaceAll }

    fun importParticipantsFromCsv(csv: String, mode: ImportMode = ImportMode.Add) {
        val imported = parseCsv(csv)
        val newParticipants = imported.map {
            GreedyParticipant(
                handler = it.handler,
                dog = it.dog,
                utn = it.utn,
                heightDivision = it.heightDivision
            )
        }
        _participants.value = when (mode) {
            ImportMode.Add -> _participants.value + newParticipants
            ImportMode.ReplaceAll -> newParticipants
        }
        if (mode == ImportMode.ReplaceAll) {
            _completedParticipants.value = emptyList()
        }
        persistState()
    }

    fun importParticipantsFromXlsx(xlsx: ByteArray, mode: ImportMode = ImportMode.Add) {
        val imported = parseXlsx(xlsx)
        val newParticipants = imported.map {
            GreedyParticipant(
                handler = it.handler,
                dog = it.dog,
                utn = it.utn,
                heightDivision = it.heightDivision
            )
        }
        _participants.value = when (mode) {
            ImportMode.Add -> _participants.value + newParticipants
            ImportMode.ReplaceAll -> newParticipants
        }
        if (mode == ImportMode.ReplaceAll) {
            _completedParticipants.value = emptyList()
        }
        persistState()
    }

    fun setSweetSpotBonus(bonus: Int) {
        pushUndo()
        if (bonus in 1..8) {
            // React behavior: bonus value is entered once; if it's 8, add 2 more.
            val normalizedBonus = if (bonus == 8) 10 else bonus
            _sweetSpotBonus.value = normalizedBonus
            // avoid double-adding if user re-submits via UI safeguards
            calculateFinalScore()
        }
        logEvent("Sweet Spot Bonus set: $bonus")
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
            val scoringButtons = _activeButtonsByZone.value[zone].count { it in listOf("X", "Y", "Z", "Sweet Spot") }
            totalScore += scoringButtons * zonePoints
        }
        totalScore += _sweetSpotBonus.value
        _score.value = totalScore
    }

    @Serializable
    data class PendingJsonExport(val filename: String, val content: String)

    @Serializable
    data class GreedyRoundResults(
        val zone1_catches: Int,
        val zone2_catches: Int,
        val zone3_catches: Int,
        val zone4_catches: Int,
        val finish_on_sweet_spot: Boolean,
        val sweet_spot_bonus: Int,
        val number_of_misses: Int,
        val all_rollers: Boolean,
        val score: Int,
        val activeButtonsByZone: List<List<String>>
    )

    @Serializable
    data class GreedyParticipantData(
        val handler: String,
        val dog: String,
        val utn: String,
        val completedAt: String
    )

    @Serializable
    data class GreedyRoundExport(
        val gameMode: String,
        val exportTimestamp: String,
        val participantData: GreedyParticipantData,
        val roundResults: GreedyRoundResults,
        val roundLog: List<String> = emptyList()
    )

    private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<PendingJsonExport?> = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    private fun exportStamp(dt: kotlinx.datetime.LocalDateTime): String {
        fun pad2(n: Int) = n.toString().padStart(2, '0')
        return buildString {
            append(dt.year)
            append(pad2(dt.monthNumber))
            append(pad2(dt.dayOfMonth))
            append('_')
            append(pad2(dt.hour))
            append(pad2(dt.minute))
            append(pad2(dt.second))
        }
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

            runCatching {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val stamp = exportStamp(now)
                val safeHandler = updatedStats.handler.replace("\\s+".toRegex(), "")
                val safeDog = updatedStats.dog.replace("\\s+".toRegex(), "")
                val filename = "Greedy_${safeHandler}_${safeDog}_$stamp.json"

                val exportData = GreedyRoundExport(
                    gameMode = "Greedy",
                    exportTimestamp = Clock.System.now().toString(),
                    participantData = GreedyParticipantData(
                        handler = updatedStats.handler,
                        dog = updatedStats.dog,
                        utn = updatedStats.utn,
                        completedAt = now.toString()
                    ),
                    roundResults = GreedyRoundResults(
                        zone1_catches = updatedStats.zone1Catches,
                        zone2_catches = updatedStats.zone2Catches,
                        zone3_catches = updatedStats.zone3Catches,
                        zone4_catches = updatedStats.zone4Catches,
                        finish_on_sweet_spot = updatedStats.finishOnSweetSpot,
                        sweet_spot_bonus = updatedStats.sweetSpotBonus,
                        number_of_misses = updatedStats.numberOfMisses,
                        all_rollers = updatedStats.allRollers,
                        score = updatedStats.score,
                        activeButtonsByZone = _activeButtonsByZone.value.map { set -> set.toList() }
                    ),
                    roundLog = _currentParticipantLog.value
                )

                _pendingJsonExport.value = PendingJsonExport(
                    filename = filename,
                    content = exportJson.encodeToString(exportData)
                )
            }

            // Move to completed
            _completedParticipants.update { it + updatedStats }

            // Remove from active queue
            _participants.update { it.drop(1) }

            // Reset state for next round and clear participant log
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
        _currentParticipantLog.value = emptyList()
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

    fun addParticipant(handler: String, dog: String, utn: String, heightDivision: String = "") {
        val participant = GreedyParticipant(handler = handler, dog = dog, utn = utn, heightDivision = heightDivision)
        _participants.update {
            if (it.isEmpty()) listOf(participant) else it + participant
        }
        persistState()
    }

    fun incrementMisses() {
        pushUndo()
        _misses.value = _misses.value + 1
        logEvent("Miss+")
    }

    fun toggleAllRollers() {
        pushUndo()
        _allRollersEnabled.update { !it }
        logEvent(if (_allRollersEnabled.value) "All Rollers enabled" else "All Rollers disabled")
    }
}
