package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

class FourWayPlayScreenModel : ScreenModel {

    @Serializable
    data class Participant(
        val handler: String,
        val dog: String,
        val utn: String,
        val heightDivision: String = "",
        val zone1Catches: Int = 0,
        val zone2Catches: Int = 0,
        val zone3Catches: Int = 0,
        val zone4Catches: Int = 0,
        val sweetSpot: Boolean = false,
        val allRollers: Boolean = false,
        val misses: Int = 0,
        val score: Int = 0,
        val hasScore: Boolean = false,
    )

    @Serializable
    data class PersistedState(
        val participants: List<Participant> = emptyList(),
        val completed: List<Participant> = emptyList()
    )

    private var dataStore: DataStore? = null
    private val persistenceKey = "FourWayPlayData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val state = Json.decodeFromString<PersistedState>(json)
                    _participants.value = state.participants
                    _completed.value = state.completed
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun persistState() {
        val store = dataStore ?: return
        val state = PersistedState(_participants.value, _completed.value)
        screenModelScope.launch {
            try {
                val json = Json.encodeToString(state)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private data class Snapshot(
        val score: Int,
        val quads: Int,
        val clickedZones: Set<Int>,
        val sweetSpot: Boolean,
        val misses: Int,
    )

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _clickedZones = MutableStateFlow(setOf<Int>())
    val clickedZones: StateFlow<Set<Int>> = _clickedZones.asStateFlow()

    private val _sweetSpotClicked = MutableStateFlow(false)
    val sweetSpotClicked: StateFlow<Boolean> = _sweetSpotClicked.asStateFlow()

    private val _fieldFlipped = MutableStateFlow(false)
    val fieldFlipped: StateFlow<Boolean> = _fieldFlipped.asStateFlow()

    private val _quads = MutableStateFlow(0)
    val quads: StateFlow<Int> = _quads.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses: StateFlow<Int> = _misses.asStateFlow()

    private val _allRollers = MutableStateFlow(false)
    val allRollers: StateFlow<Boolean> = _allRollers.asStateFlow()

    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    private val _completed = MutableStateFlow<List<Participant>>(emptyList())
    val completed: StateFlow<List<Participant>> = _completed.asStateFlow()

    private val _lastSidebarAction = MutableStateFlow("Ready for actions")
    val lastSidebarAction: StateFlow<String> = _lastSidebarAction.asStateFlow()

    private val undoStack = ArrayDeque<Snapshot>()
    private val snapshotLimit = 50

    fun handleZoneClick(zoneValue: Int) {
        if (zoneValue in _clickedZones.value) return
        pushSnapshot()
        _score.value += zoneValue
        val updatedZones = _clickedZones.value + zoneValue
        if (updatedZones.size == 4) {
            _quads.value += 1
            _clickedZones.value = emptySet()
        } else {
            _clickedZones.value = updatedZones
        }
        _zoneCatchMap[zoneValue] = (_zoneCatchMap[zoneValue] ?: 0) + 1
    }

    private val _zoneCatchMap = mutableMapOf<Int, Int>()

    fun resetScoring() {
        pushSnapshot()
        _score.value = 0
        _clickedZones.value = emptySet()
        _sweetSpotClicked.value = false
        _quads.value = 0
        _misses.value = 0
        _zoneCatchMap.clear()
    }

    fun moveToNextParticipant() {
        if (_participants.value.isEmpty()) return
        val current = _participants.value.first()
        val updatedCurrent = current.copy(
            zone1Catches = _zoneCatchMap[1] ?: 0,
            zone2Catches = _zoneCatchMap[2] ?: 0,
            zone3Catches = _zoneCatchMap[3] ?: 0,
            zone4Catches = _zoneCatchMap[4] ?: 0,
            sweetSpot = _sweetSpotClicked.value,
            allRollers = _allRollers.value,
            misses = _misses.value,
            score = _score.value,
            hasScore = (_zoneCatchMap.isNotEmpty() || _sweetSpotClicked.value || _misses.value > 0)
        )
        if (updatedCurrent.hasScore) {
            _completed.value = _completed.value + updatedCurrent
        } else {
            _participants.value = _participants.value.drop(1) + updatedCurrent
        }
        _participants.value = _participants.value.drop(1)
        resetScoring()
        recordSidebarAction("Next participant saved")
        persistState()
    }

    fun getParticipantsForExport(): List<Participant> = _completed.value.filter { it.hasScore }

    fun addParticipant(handler: String, dog: String, utn: String, heightDivision: String = "") {
        _participants.value = _participants.value + Participant(handler, dog, utn, heightDivision = heightDivision)
        recordSidebarAction("Added $handler & $dog")
        persistState()
    }

    fun clearParticipants() {
        _participants.value = emptyList()
        recordSidebarAction("Participants cleared")
        persistState()
    }

    fun moveToPreviousParticipant() {
        if (_participants.value.size <= 1) return
        val list = _participants.value
        val last = list.last()
        _participants.value = listOf(last) + list.dropLast(1)
        recordSidebarAction("Previous participant")
        persistState()
    }

    fun skipParticipant() {
        moveToNextParticipant()
        recordSidebarAction("Participant skipped")
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val participants = imported.map { Participant(it.handler, it.dog, it.utn, it.heightDivision) }
        _participants.value = participants
        _completed.value = emptyList()
        recordSidebarAction("Imported ${participants.size} from CSV")
        persistState()
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val participants = imported.map { Participant(it.handler, it.dog, it.utn, it.heightDivision) }
        _participants.value = participants
        _completed.value = emptyList()
        recordSidebarAction("Imported ${participants.size} from XLSX")
        persistState()
    }

    fun exportParticipantsAsCsv(): String {
        return buildString {
            append("Handler,Dog,UTN\n")
            _participants.value.forEach { p ->
                append("${p.handler},${p.dog},${p.utn}\n")
            }
        }
    }

    fun exportLog(): String {
        return "Log export not implemented yet."
    }

    fun recordSidebarAction(action: String) {
        _lastSidebarAction.value = action
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeLast()
            _score.value = snapshot.score
            _quads.value = snapshot.quads
            _clickedZones.value = snapshot.clickedZones
            _sweetSpotClicked.value = snapshot.sweetSpot
            _misses.value = snapshot.misses
        }
    }

    private fun pushSnapshot() {
        if (undoStack.size >= snapshotLimit) undoStack.removeFirst()
        undoStack.add(Snapshot(
            score = _score.value,
            quads = _quads.value,
            clickedZones = _clickedZones.value,
            sweetSpot = _sweetSpotClicked.value,
            misses = _misses.value
        ))
    }

    fun toggleSweetSpot() {
        pushSnapshot()
        _sweetSpotClicked.value = !_sweetSpotClicked.value
        if (_sweetSpotClicked.value) _score.value += 1 else _score.value -= 1 // Example scoring
    }

    fun addMiss() {
        pushSnapshot()
        _misses.value += 1
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
    }
}
