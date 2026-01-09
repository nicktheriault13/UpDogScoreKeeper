package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class FourWayPlayScreenModel : ScreenModel {

    data class Participant(
        val handler: String,
        val dog: String,
        val utn: String,
        val hasScore: Boolean = false,
    )

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

    private val _participants = MutableStateFlow(
        listOf(
            Participant("Alex Vega", "Bolt", "UTN-001"),
            Participant("Jamie Reed", "Skye", "UTN-002"),
            Participant("Morgan Lee", "Nova", "UTN-003"),
            Participant("Harper Quinn", "Echo", "UTN-004"),
        )
    )
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

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
    }

    fun handleSweetSpotClick() {
        pushSnapshot()
        if (_sweetSpotClicked.value) {
            _score.value -= 2
        } else {
            _score.value += 2
        }
        _sweetSpotClicked.value = !_sweetSpotClicked.value
    }

    fun registerMiss() {
        pushSnapshot()
        _misses.value = max(0, _misses.value + 1)
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
    }

    fun resetScoring() {
        pushSnapshot()
        _score.value = 0
        _clickedZones.value = emptySet()
        _sweetSpotClicked.value = false
        _quads.value = 0
        _misses.value = 0
    }

    fun toggleAllRollers() {
        _allRollers.value = !_allRollers.value
    }

    fun toggleSidebar() {
        _sidebarCollapsed.value = !_sidebarCollapsed.value
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        _participants.value = _participants.value + Participant(handler, dog, utn)
        recordSidebarAction("Added $handler & $dog")
    }

    fun clearParticipants() {
        _participants.value = emptyList()
        recordSidebarAction("Participants cleared")
    }

    fun moveToNextParticipant() {
        if (_participants.value.size <= 1) return
        val current = _participants.value.first()
        _participants.value = _participants.value.drop(1) + current
        recordSidebarAction("Next participant")
    }

    fun moveToPreviousParticipant() {
        if (_participants.value.size <= 1) return
        val list = _participants.value
        val last = list.last()
        _participants.value = listOf(last) + list.dropLast(1)
        recordSidebarAction("Previous participant")
    }

    fun skipParticipant() {
        moveToNextParticipant()
        recordSidebarAction("Participant skipped")
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val participants = imported.map { Participant(it.handler, it.dog, it.utn) }
        _participants.value = participants
        recordSidebarAction("Imported ${participants.size} from CSV")
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val participants = imported.map { Participant(it.handler, it.dog, it.utn) }
        _participants.value = participants
        recordSidebarAction("Imported ${participants.size} from XLSX")
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
        if (undoStack.isEmpty()) return
        val snapshot = undoStack.removeLast()
        _score.value = snapshot.score
        _quads.value = snapshot.quads
        _clickedZones.value = snapshot.clickedZones
        _sweetSpotClicked.value = snapshot.sweetSpot
        _misses.value = snapshot.misses
    }

    private fun pushSnapshot() {
        val snapshot = Snapshot(
            score = _score.value,
            quads = _quads.value,
            clickedZones = _clickedZones.value,
            sweetSpot = _sweetSpotClicked.value,
            misses = _misses.value
        )
        undoStack.addLast(snapshot)
        if (undoStack.size > snapshotLimit) {
            undoStack.removeFirst()
        }
    }
}
