package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class FireballScreenModel : ScreenModel {

    companion object {
        /**
         * Fire Ball 3x3 scoring grid used by the UI. `null` denotes an empty cell.
         */
        val zoneValueGrid: List<List<Int?>> = listOf(
            listOf(8, 7, 5),
            listOf(6, 4, 2),
            listOf(3, 1, null)
        )

        fun zoneValue(row: Int, col: Int): Int? = zoneValueGrid
            .getOrNull(row)
            ?.getOrNull(col)

        private const val TIMER_DEFAULT_SECONDS = 64
    }

    // --- Persistent state container ---

    private val _activeParticipant = MutableStateFlow<FireballParticipant?>(null)
    val activeParticipant: StateFlow<FireballParticipant?> = _activeParticipant.asStateFlow()

    private val _participantsQueue = MutableStateFlow<List<FireballParticipant>>(emptyList())
    val participantsQueue: StateFlow<List<FireballParticipant>> = _participantsQueue.asStateFlow()

    private val _clickedZones = MutableStateFlow<Set<FireballGridPoint>>(emptySet())
    val clickedZones: StateFlow<Set<FireballGridPoint>> = _clickedZones.asStateFlow()

    private val _fireballZones = MutableStateFlow<Set<FireballGridPoint>>(emptySet())
    val fireballZones: StateFlow<Set<FireballGridPoint>> = _fireballZones.asStateFlow()

    private val _isFireballActive = MutableStateFlow(false)
    val isFireballActive: StateFlow<Boolean> = _isFireballActive.asStateFlow()

    private val _sweetSpotBonusAwarded = MutableStateFlow(false)
    val sweetSpotBonusAwarded: StateFlow<Boolean> = _sweetSpotBonusAwarded.asStateFlow()

    private val _allRollersActive = MutableStateFlow(false)
    val allRollersActive: StateFlow<Boolean> = _allRollersActive.asStateFlow()

    private val _isFieldFlipped = MutableStateFlow(false)
    val isFieldFlipped: StateFlow<Boolean> = _isFieldFlipped.asStateFlow()

    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    val sidebarMessage = MutableStateFlow("")

    // Derived scores for UI
    private val _currentBoardScore = MutableStateFlow(0)
    val currentBoardScore: StateFlow<Int> = _currentBoardScore.asStateFlow()

    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore.asStateFlow()

    // Timer
    private val _timerSecondsRemaining = MutableStateFlow(0)
    val timerSecondsRemaining: StateFlow<Int> = _timerSecondsRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    // Persistence
    private var dataStore: DataStore? = null
    private val persistenceKey = "FireballData.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val raw = runCatching { store.load(persistenceKey) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val snapshot = json.decodeFromString<FireballPersistedState>(raw)
                    restore(snapshot)
                }.onFailure {
                    // If corrupt, don't crash; just start clean
                    sidebarMessage.value = "Failed to load saved game"
                }
            }
            recomputeScores()
        }
    }

    private fun restore(state: FireballPersistedState) {
        _activeParticipant.value = state.activeParticipant
        _participantsQueue.value = state.queue
        _clickedZones.value = state.clickedZones.map { FireballGridPoint(it.row, it.col) }.toSet()
        _fireballZones.value = state.fireballZones.map { FireballGridPoint(it.row, it.col) }.toSet()
        _isFireballActive.value = state.isFireballActive
        _sweetSpotBonusAwarded.value = state.sweetSpotBonusAwarded
        _allRollersActive.value = state.allRollersActive
        _isFieldFlipped.value = state.isFieldFlipped
        _sidebarCollapsed.value = state.sidebarCollapsed
        sidebarMessage.value = state.sidebarMessage
    }

    private fun persistState() {
        val store = dataStore ?: return
        val snapshot = FireballPersistedState(
            activeParticipant = _activeParticipant.value,
            queue = _participantsQueue.value,
            clickedZones = _clickedZones.value.map { FireballGridPointDTO(it.row, it.col) },
            fireballZones = _fireballZones.value.map { FireballGridPointDTO(it.row, it.col) },
            isFireballActive = _isFireballActive.value,
            sweetSpotBonusAwarded = _sweetSpotBonusAwarded.value,
            allRollersActive = _allRollersActive.value,
            isFieldFlipped = _isFieldFlipped.value,
            sidebarCollapsed = _sidebarCollapsed.value,
            sidebarMessage = sidebarMessage.value
        )
        screenModelScope.launch {
            runCatching { store.save(persistenceKey, json.encodeToString(snapshot)) }
        }
    }

    // --- Gameplay actions ---

    fun toggleSidebar() {
        _sidebarCollapsed.value = !_sidebarCollapsed.value
        persistState()
    }

    fun clearParticipantsQueue() {
        _activeParticipant.value = null
        _participantsQueue.value = emptyList()
        clearBoard()
        recomputeScores()
        persistState()
    }

    fun addParticipant(participant: FireballParticipant) {
        if (_activeParticipant.value == null) {
            _activeParticipant.value = participant
        } else {
            _participantsQueue.value = _participantsQueue.value + participant
        }
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText).map { FireballParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(imported)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData).map { FireballParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(imported)
    }

    private fun applyImportedPlayers(players: List<FireballParticipant>) {
        if (players.isEmpty()) return
        _activeParticipant.value = players.first()
        _participantsQueue.value = players.drop(1)
        clearBoard()
        recomputeScores()
        persistState()
    }

    fun handleZoneClick(row: Int, col: Int) {
        val value = zoneValue(row, col) ?: return
        val point = FireballGridPoint(row, col)

        // If already green and we are NOT in fireball mode, ignore (UI disables)
        if (_clickedZones.value.contains(point) && !_isFireballActive.value) return

        // If already fireball, ignore toggling
        if (_fireballZones.value.contains(point)) return

        // First click marks as regular catch, second click (when fireball mode active) marks as fireball
        if (_clickedZones.value.contains(point) && _isFireballActive.value) {
            _fireballZones.value = _fireballZones.value + point
        } else {
            _clickedZones.value = _clickedZones.value + point
        }

        // If the user clicks the 8-zone as fireball, we still allow.
        recomputeScores(latestClickedValue = value)
        persistState()

        // If all non-null cells are clicked at least once -> auto-advance board
        if (isBoardComplete()) {
            finalizeBoardAndCarryToParticipant()
        }
    }

    private fun isBoardComplete(): Boolean {
        val required = mutableSetOf<FireballGridPoint>()
        zoneValueGrid.forEachIndexed { r, cols ->
            cols.forEachIndexed { c, v ->
                if (v != null) required.add(FireballGridPoint(r, c))
            }
        }
        return required.isNotEmpty() && _clickedZones.value.containsAll(required)
    }

    private fun finalizeBoardAndCarryToParticipant() {
        // When a board completes, add this board's score into participant stats
        val participant = _activeParticipant.value ?: return

        val board = computeBoardBreakdown()
        val newStats = participant.stats.copy(
            nonFireballPoints = participant.stats.nonFireballPoints + board.nonFireballPoints,
            fireballPoints = participant.stats.fireballPoints + board.fireballPoints,
            sweetSpotBonus = participant.stats.sweetSpotBonus + board.sweetSpotBonus,
            completedBoards = participant.stats.completedBoards + 1,
            totalPoints = participant.stats.totalPoints + board.totalPoints,
            completedBoardsTotal = (participant.stats.completedBoardsTotal + board.totalPoints),
            highestZone = maxOf(participant.stats.highestZone ?: 0, board.highestZone ?: 0).takeIf { it > 0 },
            allRollers = _allRollersActive.value
        )

        _activeParticipant.value = participant.copy(stats = newStats)

        // clear board for next board for same participant
        clearBoardInternal()
        recomputeScores()
        sidebarMessage.value = "Board complete"
        persistState()
    }

    fun nextParticipant() {
        val current = _activeParticipant.value
        val next = _participantsQueue.value.firstOrNull()

        if (current == null && next == null) return

        if (next == null) {
            // Nothing to advance to
            sidebarMessage.value = "No next participant"
            persistState()
            return
        }

        _participantsQueue.value = _participantsQueue.value.drop(1)
        _activeParticipant.value = next

        clearBoardInternal()
        recomputeScores()
        persistState()
    }

    fun skipParticipant() {
        val current = _activeParticipant.value ?: return
        val next = _participantsQueue.value.firstOrNull()

        if (next == null) {
            // Only one participant; skipping does nothing
            sidebarMessage.value = "No one to skip to"
            persistState()
            return
        }

        _participantsQueue.value = _participantsQueue.value.drop(1) + current
        _activeParticipant.value = next

        clearBoardInternal()
        recomputeScores()
        persistState()
    }

    fun clearBoard() {
        clearBoardInternal()
        recomputeScores()
        persistState()
    }

    private fun clearBoardInternal() {
        _clickedZones.value = emptySet()
        _fireballZones.value = emptySet()
        _isFireballActive.value = false
        _sweetSpotBonusAwarded.value = false
    }

    fun resetGame() {
        // Reset scores for ALL participants
        _activeParticipant.value = _activeParticipant.value?.copy(stats = FireballParticipantStats())
        _participantsQueue.value = _participantsQueue.value.map { it.copy(stats = FireballParticipantStats()) }
        clearBoardInternal()
        recomputeScores()
        sidebarMessage.value = "Game reset"
        persistState()
    }

    fun toggleAllRollers() {
        _allRollersActive.value = !_allRollersActive.value
        // allRollers affects scoring only at export/participant level; keep persisted
        persistState()
    }

    fun toggleFireball() {
        _isFireballActive.value = !_isFireballActive.value
        persistState()
    }

    fun toggleManualSweetSpot() {
        _sweetSpotBonusAwarded.value = !_sweetSpotBonusAwarded.value
        recomputeScores()
        persistState()
    }

    fun toggleFieldOrientation() {
        _isFieldFlipped.value = !_isFieldFlipped.value
        persistState()
    }

    // --- Timer ---

    fun startRoundTimer(durationSeconds: Int = TIMER_DEFAULT_SECONDS) {
        timerJob?.cancel()
        _timerSecondsRemaining.value = durationSeconds
        _isTimerRunning.value = true

        timerJob = screenModelScope.launch {
            var remaining = durationSeconds
            while (remaining >= 0 && _isTimerRunning.value) {
                _timerSecondsRemaining.value = remaining
                if (remaining == 0) {
                    _isTimerRunning.value = false
                    break
                }
                delay(1000)
                remaining -= 1
            }
        }
    }

    fun stopRoundTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        timerJob = null
        _timerSecondsRemaining.value = 0
    }

    fun resetRoundTimer() {
        stopRoundTimer()
    }

    fun openHelp() {
        sidebarMessage.value = "Tap a zone to score. Fireball Mode: second-tap a green zone to make it Fireball (double points). Sweet Spot Bonus adds +4 (or +8 if Fireball active)."
        persistState()
    }

    // --- Scoring ---

    private data class BoardBreakdown(
        val nonFireballPoints: Int,
        val fireballPoints: Int,
        val sweetSpotBonus: Int,
        val totalPoints: Int,
        val highestZone: Int?
    )

    private fun computeBoardBreakdown(): BoardBreakdown {
        var nonFire = 0
        var fire = 0
        var highest: Int? = null

        val clicked = _clickedZones.value
        val fireballs = _fireballZones.value

        clicked.forEach { p ->
            val value = zoneValue(p.row, p.col) ?: return@forEach
            if (p in fireballs) {
                fire += value * 2
            } else {
                nonFire += value
            }
            highest = maxOf(highest ?: 0, value)
        }

        val sweetSpotBonus = if (_sweetSpotBonusAwarded.value) {
            if (_isFireballActive.value) 8 else 4
        } else 0

        val total = nonFire + fire + sweetSpotBonus

        return BoardBreakdown(
            nonFireballPoints = nonFire,
            fireballPoints = fire,
            sweetSpotBonus = sweetSpotBonus,
            totalPoints = total,
            highestZone = highest
        )
    }

    private fun recomputeScores(latestClickedValue: Int? = null) {
        val participant = _activeParticipant.value
        val board = computeBoardBreakdown()

        _currentBoardScore.value = board.totalPoints

        val already = participant?.stats?.totalPoints ?: 0
        _totalScore.value = already + board.totalPoints

        // optionally provide a small UI message
        if (latestClickedValue != null) {
            sidebarMessage.value = "+$latestClickedValue" + if (_isFireballActive.value) " (fireball mode)" else ""
        }
    }

    // --- Export ---

    suspend fun exportAsJson(): String {
        val state = FireballExportSnapshot(
            activeParticipant = _activeParticipant.value,
            queue = _participantsQueue.value,
            allRollers = _allRollersActive.value
        )
        return json.encodeToString(state)
    }
}

@Serializable
data class FireballGridPointDTO(val row: Int, val col: Int)

@Serializable
data class FireballGridPoint(val row: Int, val col: Int)

@Serializable
data class FireballParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val stats: FireballParticipantStats = FireballParticipantStats()
)

@Serializable
data class FireballParticipantStats(
    val nonFireballPoints: Int = 0,
    val fireballPoints: Int = 0,
    val sweetSpotBonus: Int = 0,
    val totalPoints: Int = 0,
    val highestZone: Int? = null,
    val allRollers: Boolean = false,
    val completedBoards: Int = 0,
    val completedBoardsTotal: Int = 0
)

@Serializable
data class FireballPersistedState(
    val activeParticipant: FireballParticipant? = null,
    val queue: List<FireballParticipant> = emptyList(),
    val clickedZones: List<FireballGridPointDTO> = emptyList(),
    val fireballZones: List<FireballGridPointDTO> = emptyList(),
    val isFireballActive: Boolean = false,
    val sweetSpotBonusAwarded: Boolean = false,
    val allRollersActive: Boolean = false,
    val isFieldFlipped: Boolean = false,
    val sidebarCollapsed: Boolean = false,
    val sidebarMessage: String = ""
)

@Serializable
data class FireballExportSnapshot(
    val activeParticipant: FireballParticipant?,
    val queue: List<FireballParticipant>,
    val allRollers: Boolean
)
