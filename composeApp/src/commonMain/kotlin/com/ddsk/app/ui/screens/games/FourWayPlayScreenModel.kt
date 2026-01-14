package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FourWayPlayScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

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
        // Desktop builds don't provide Dispatchers.Main by default.
        // Keep persistence IO/deserialization off Main.
        scope.launch {
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
        // Desktop builds don't provide Dispatchers.Main by default.
        scope.launch {
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

    private fun pushSnapshot() {
        if (undoStack.size >= snapshotLimit) undoStack.removeFirst()
        undoStack.add(
            Snapshot(
                score = _score.value,
                quads = _quads.value,
                clickedZones = _clickedZones.value,
                sweetSpot = _sweetSpotClicked.value,
                misses = _misses.value
            )
        )
    }

    // ---- JSON export (triggered on Next Team) ----

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    data class PendingJsonExport(
        val filename: String,
        val content: String
    )

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<PendingJsonExport?> = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    @Serializable
    private data class FourWayPlayRoundExport(
        val gameMode: String,
        val exportTimestamp: String,
        val participantData: FourWayPlayParticipantData,
        val roundResults: FourWayPlayRoundResults,
        val roundLog: List<String>
    )

    @Serializable
    private data class FourWayPlayParticipantData(
        val handler: String,
        val dog: String,
        val completedAt: String
    )

    @Serializable
    private data class FourWayPlayRoundResults(
        val zone1Catches: Int,
        val zone2Catches: Int,
        val zone3Catches: Int,
        val zone4Catches: Int,
        val sweetSpot: String,
        val allRollers: String,
        val totalScore: Int,
        val misses: Int
    )

    private fun fourWayPlayTimestamp(now: kotlinx.datetime.LocalDateTime): String {
        fun pad2(n: Int) = n.toString().padStart(2, '0')
        return buildString {
            append(now.year)
            append(pad2(now.monthNumber))
            append(pad2(now.dayOfMonth))
            append('_')
            append(pad2(now.hour))
            append(pad2(now.minute))
            append(pad2(now.second))
        }
    }

    // Keep filenames deterministic and safe across platforms.
    // React behavior removes whitespace entirely.
    private fun safeNamePart(value: String, fallback: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return fallback
        return trimmed.replace("\\s+".toRegex(), "")
    }

    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog: StateFlow<List<String>> = _currentParticipantLog.asStateFlow()

    private fun logRoundEvent(event: String) {
        // Match React style: ISO-8601 timestamp + message
        val ts = Clock.System.now().toString()
        _currentParticipantLog.value = _currentParticipantLog.value + "$ts: $event"
    }

    private fun currentTeamDisplay(): String {
        val p = _participants.value.firstOrNull()
        if (p == null) return "No team loaded"
        return buildString {
            append(p.handler)
            if (p.dog.isNotBlank()) {
                append(" & ")
                append(p.dog)
            }
        }
    }

    private fun snapshotStateForLog(): String {
        return "score=${_score.value}, quads=${_quads.value}, misses=${_misses.value}, allRollers=${_allRollers.value}, sweetSpot=${_sweetSpotClicked.value}"
    }

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

        logRoundEvent("Zone $zoneValue pressed (${currentTeamDisplay()}) | ${snapshotStateForLog()}")
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

        logRoundEvent("Reset Scoring (${currentTeamDisplay()}) | ${snapshotStateForLog()}")
    }

    fun moveToNextParticipant() {
        if (_participants.value.isEmpty()) return

        logRoundEvent("Next pressed (${currentTeamDisplay()}) | ${snapshotStateForLog()}")

        val current = _participants.value.first()

        // Finalize current scoring snapshot BEFORE we clear/reset.
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

        // Emit per-round JSON export when a round is actually committed.
        if (updatedCurrent.hasScore) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val stamp = fourWayPlayTimestamp(now)
            val filename = "4WayPlay_${safeNamePart(updatedCurrent.handler, "Handler")}_${safeNamePart(updatedCurrent.dog, "Dog")}_${stamp}.json"

            val exportData = FourWayPlayRoundExport(
                gameMode = "4-Way Play",
                exportTimestamp = Clock.System.now().toString(),
                participantData = FourWayPlayParticipantData(
                    handler = updatedCurrent.handler,
                    dog = updatedCurrent.dog,
                    completedAt = now.toString()
                ),
                roundResults = FourWayPlayRoundResults(
                    zone1Catches = updatedCurrent.zone1Catches,
                    zone2Catches = updatedCurrent.zone2Catches,
                    zone3Catches = updatedCurrent.zone3Catches,
                    zone4Catches = updatedCurrent.zone4Catches,
                    sweetSpot = if (updatedCurrent.sweetSpot) "Yes" else "No",
                    allRollers = if (updatedCurrent.allRollers) "Yes" else "No",
                    totalScore = updatedCurrent.score,
                    misses = updatedCurrent.misses
                ),
                roundLog = _currentParticipantLog.value
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )
        }

        if (updatedCurrent.hasScore) {
            _completed.value = _completed.value + updatedCurrent
        } else {
            _participants.value = _participants.value.drop(1) + updatedCurrent
        }
        _participants.value = _participants.value.drop(1)

        // IMPORTANT: don't call resetScoring() here, because it now logs.
        // Do a silent reset for the next participant.
        pushSnapshot()
        _score.value = 0
        _clickedZones.value = emptySet()
        _sweetSpotClicked.value = false
        _quads.value = 0
        _misses.value = 0
        _zoneCatchMap.clear()

        _currentParticipantLog.value = emptyList()

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
        logRoundEvent("Previous pressed | now active=${currentTeamDisplay()}")
        persistState()
    }

    fun skipParticipant() {
        logRoundEvent("Skip pressed (${currentTeamDisplay()})")
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
        // Keep a per-participant log buffer for JSON export.
        logRoundEvent(action)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeLast()
            _score.value = snapshot.score
            _quads.value = snapshot.quads
            _clickedZones.value = snapshot.clickedZones
            _sweetSpotClicked.value = snapshot.sweetSpot
            _misses.value = snapshot.misses

            logRoundEvent("Undo | ${snapshotStateForLog()}")
        }
    }

    fun toggleSweetSpot() {
        pushSnapshot()
        _sweetSpotClicked.value = !_sweetSpotClicked.value
        if (_sweetSpotClicked.value) _score.value += 1 else _score.value -= 1 // Example scoring

        logRoundEvent("Sweet Spot pressed (${currentTeamDisplay()}) | ${snapshotStateForLog()}")
    }

    fun addMiss() {
        pushSnapshot()
        _misses.value += 1
        logRoundEvent("Miss pressed (${currentTeamDisplay()}) | ${snapshotStateForLog()}")
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
        logRoundEvent("Flip Field pressed | flipped=${_fieldFlipped.value}")
    }
}
