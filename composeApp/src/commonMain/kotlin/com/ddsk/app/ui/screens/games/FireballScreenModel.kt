package com.ddsk.app.ui.screens.games

// NOTE: this file was previously truncated and contained an unterminated comment causing compilation to fail.

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class FireballScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    companion object {
        /** Fire Ball scoring grid values (8 zones). */
        val zoneValueGrid: List<List<Int?>> = listOf(
            listOf(8, 7, 5),
            listOf(6, 4, 2),
            listOf(3, 1, null)
        )

        fun zoneValue(row: Int, col: Int): Int? = zoneValueGrid.getOrNull(row)?.getOrNull(col)

        private const val TIMER_DEFAULT_SECONDS = 64

        private val REQUIRED_ZONES: Set<FireballGridPoint> = buildSet {
            zoneValueGrid.forEachIndexed { r, cols ->
                cols.forEachIndexed { c, v ->
                    if (v != null) add(FireballGridPoint(r, c))
                }
            }
        }
    }

    // ---- state ----

    private val _activeParticipant = MutableStateFlow<FireballParticipant?>(null)
    val activeParticipant: StateFlow<FireballParticipant?> = _activeParticipant.asStateFlow()

    private val _participantsQueue = MutableStateFlow<List<FireballParticipant>>(emptyList())
    val participantsQueue: StateFlow<List<FireballParticipant>> = _participantsQueue.asStateFlow()

    private val _completedParticipants = MutableStateFlow<List<FireballParticipant>>(emptyList())
    val completedParticipants: StateFlow<List<FireballParticipant>> = _completedParticipants.asStateFlow()

    private val _clickedZones = MutableStateFlow<Set<FireballGridPoint>>(emptySet())
    val clickedZones: StateFlow<Set<FireballGridPoint>> = _clickedZones.asStateFlow()

    private val _fireballZones = MutableStateFlow<Set<FireballGridPoint>>(emptySet())
    val fireballZones: StateFlow<Set<FireballGridPoint>> = _fireballZones.asStateFlow()

    // Undo stack: stores board state snapshots
    private data class BoardSnapshot(
        val clickedZones: Set<FireballGridPoint>,
        val fireballZones: Set<FireballGridPoint>
    )
    private val undoStack = ArrayDeque<BoardSnapshot>()

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

    private val _currentBoardScore = MutableStateFlow(0)
    val currentBoardScore: StateFlow<Int> = _currentBoardScore.asStateFlow()

    private val _totalScore = MutableStateFlow(0)
    val totalScore: StateFlow<Int> = _totalScore.asStateFlow()

    // Current board's regular and fireball points (live updates during gameplay)
    private val _currentBoardRegularPoints = MutableStateFlow(0)
    val currentBoardRegularPoints: StateFlow<Int> = _currentBoardRegularPoints.asStateFlow()

    private val _currentBoardFireballPoints = MutableStateFlow(0)
    val currentBoardFireballPoints: StateFlow<Int> = _currentBoardFireballPoints.asStateFlow()

    // Timer
    private val _timerSecondsRemaining = MutableStateFlow(0)
    val timerSecondsRemaining: StateFlow<Int> = _timerSecondsRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null

    // Per-participant action log (efficient: only keep current participant log in memory)
    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog: StateFlow<List<String>> = _currentParticipantLog.asStateFlow()

    private fun logEvent(message: String) {
        val ts = Clock.System.now().toString()
        _currentParticipantLog.value = (_currentParticipantLog.value + "$ts: $message").takeLast(300)
    }

    // Persistence
    private var dataStore: DataStore? = null
    private val persistenceKey = "FireballData.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val exportJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val raw = runCatching { store.load(persistenceKey) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val snapshot = json.decodeFromString<FireballPersistedState>(raw)
                    restore(snapshot)
                }.onFailure {
                    sidebarMessage.value = "Failed to load saved game"
                }
            }
            recomputeScores()
        }
    }

    private fun restore(state: FireballPersistedState) {
        _activeParticipant.value = state.activeParticipant
        _participantsQueue.value = state.queue
        _completedParticipants.value = state.completed
        _currentParticipantLog.value = state.currentParticipantLog
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
            completed = _completedParticipants.value,
            currentParticipantLog = _currentParticipantLog.value,
            clickedZones = _clickedZones.value.map { FireballGridPointDTO(it.row, it.col) },
            fireballZones = _fireballZones.value.map { FireballGridPointDTO(it.row, it.col) },
            isFireballActive = _isFireballActive.value,
            sweetSpotBonusAwarded = _sweetSpotBonusAwarded.value,
            allRollersActive = _allRollersActive.value,
            isFieldFlipped = _isFieldFlipped.value,
            sidebarCollapsed = _sidebarCollapsed.value,
            sidebarMessage = sidebarMessage.value
        )
        scope.launch {
            runCatching { store.save(persistenceKey, json.encodeToString(snapshot)) }
        }
    }

    // ---- Participants ----

    fun toggleSidebar() {
        _sidebarCollapsed.value = !_sidebarCollapsed.value
        persistState()
    }

    fun clearParticipantsQueue() {
        _activeParticipant.value = null
        _participantsQueue.value = emptyList()
        _completedParticipants.value = emptyList()
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

    enum class ImportMode { ReplaceAll, Add }

    private fun applyImportedPlayers(players: List<FireballParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return

        when (mode) {
            ImportMode.ReplaceAll -> {
                _activeParticipant.value = players.firstOrNull()
                _participantsQueue.value = players.drop(1)
                _completedParticipants.value = emptyList()
                _currentParticipantLog.value = emptyList()
            }

            ImportMode.Add -> {
                // If nothing loaded yet, imported first becomes active.
                if (_activeParticipant.value == null) {
                    _activeParticipant.value = players.firstOrNull()
                    _participantsQueue.value = players.drop(1)
                } else {
                    _participantsQueue.value = _participantsQueue.value + players
                }
            }
        }

        clearBoardInternal()
        recomputeScores()
        persistState()
    }

    // Backwards-compatible behavior (used by existing calls): replace all.
    private fun applyImportedPlayers(players: List<FireballParticipant>) {
        applyImportedPlayers(players, ImportMode.ReplaceAll)
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseCsv(csvText).map {
            FireballParticipant(it.handler, it.dog, it.utn, heightDivision = it.heightDivision)
        }
        applyImportedPlayers(imported, mode)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseXlsx(xlsxData).map {
            FireballParticipant(it.handler, it.dog, it.utn, heightDivision = it.heightDivision)
        }
        applyImportedPlayers(imported, mode)
    }

    // ---- Board / scoring ----

    private fun pushUndo() {
        val snapshot = BoardSnapshot(
            clickedZones = _clickedZones.value,
            fireballZones = _fireballZones.value
        )
        undoStack.add(snapshot)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) {
            sidebarMessage.value = "Nothing to undo"
            return
        }

        val snapshot = undoStack.removeAt(undoStack.lastIndex)
        // Force state update with sentinel values to ensure desktop recomposition
        val sentinelPoint = FireballGridPoint(-1, -1)
        _clickedZones.value = setOf(sentinelPoint)
        _fireballZones.value = setOf(sentinelPoint)

        // Now restore the snapshot
        _clickedZones.value = snapshot.clickedZones
        _fireballZones.value = snapshot.fireballZones

        recomputeScores()
        logEvent("Undo")
        persistState()
    }

    fun clearBoard() {
        logEvent("Clear Board")
        undoStack.clear()
        clearBoardInternal(resetFireballMode = true)
        recomputeScores()
        persistState()
    }

    private fun clearBoardInternal(resetFireballMode: Boolean = true) {
        // Force state updates to trigger recomposition on desktop
        // Set to sentinel values first, then clear
        val sentinelPoint = FireballGridPoint(-1, -1)
        _clickedZones.value = setOf(sentinelPoint)
        _fireballZones.value = setOf(sentinelPoint)

        // Now clear to empty
        _clickedZones.value = emptySet()
        _fireballZones.value = emptySet()

        if (resetFireballMode) {
            _isFireballActive.value = false
        }
        _sweetSpotBonusAwarded.value = false
    }

    fun handleZoneClick(row: Int, col: Int) {
        val value = zoneValue(row, col) ?: return

        // Push current state to undo stack before making changes
        pushUndo()

        logEvent("Zone $value")
        val point = FireballGridPoint(row, col)

        val clicked = _clickedZones.value
        val fireballs = _fireballZones.value

        val isGreen = clicked.contains(point)
        val isOrange = fireballs.contains(point)

        if (_isFireballActive.value) {
            // React: toggleable in fireball mode
            if (isGreen || isOrange) {
                _clickedZones.value = clicked - point
                _fireballZones.value = fireballs - point
            } else {
                _fireballZones.value = fireballs + point
            }
        } else {
            // React: not toggleable in non-fireball mode
            if (isGreen || isOrange) return
            _clickedZones.value = clicked + point
        }

        recomputeScores(latestClickedValue = value)
        persistState()

        if (isBoardComplete()) {
            finalizeBoardAndCarryToParticipant()
        }
    }

    private fun isBoardComplete(): Boolean {
        val union = _clickedZones.value + _fireballZones.value
        return REQUIRED_ZONES.isNotEmpty() && union.containsAll(REQUIRED_ZONES)
    }

    private data class BoardBreakdown(
        val nonFireballPoints: Int,
        val fireballPoints: Int,
        val totalPoints: Int,
        val highestZone: Int?
    )

    private fun computeBoardBreakdown(): BoardBreakdown {
        var nonFire = 0
        var fire = 0
        var highest: Int? = null

        val clicked = _clickedZones.value
        val fireballs = _fireballZones.value

        // IMPORTANT: treat zones in fireballs as fireball points even if they also appear in clicked.
        // This prevents mis-attribution (fireball points showing up in non-fireball) if a point
        // accidentally ends up in both sets.
        val union = clicked + fireballs

        union.forEach { p ->
            val v = zoneValue(p.row, p.col) ?: return@forEach
            if (p in fireballs) {
                fire += v * 2
            } else {
                nonFire += v
            }
            highest = maxOf(highest ?: 0, v)
        }

        return BoardBreakdown(
            nonFireballPoints = nonFire,
            fireballPoints = fire,
            totalPoints = nonFire + fire,
            highestZone = highest
        )
    }

    private fun finalizeBoardAndCarryToParticipant() {
        val participant = _activeParticipant.value ?: return
        val board = computeBoardBreakdown()

        val updatedHighest = maxOf(participant.highestZone ?: 0, board.highestZone ?: 0).takeIf { it > 0 }

        val updated = participant.copy(
            nonFireballPoints = participant.nonFireballPoints + board.nonFireballPoints,
            fireballPoints = participant.fireballPoints + board.fireballPoints,
            completedBoards = participant.completedBoards + 1,
            completedBoardsTotal = participant.completedBoardsTotal + board.totalPoints,
            highestZone = updatedHighest,
            allRollers = _allRollersActive.value,
            totalPoints = (participant.completedBoardsTotal + board.totalPoints) + participant.sweetSpotBonus
        )

        _activeParticipant.value = updated

        clearBoardInternal(resetFireballMode = false) // Preserve fireball mode for next board
        recomputeScores()
        sidebarMessage.value = "Board complete"
        persistState()
    }

    private data class PointsSnapshot(
        val nonFireballPoints: Int,
        val fireballPoints: Int,
        val totalPoints: Int,
        val highestZone: Int?
    )

    /**
     * React-style current points:
     * - participant.nonFireballPoints/fireballPoints represent COMPLETED boards totals only
     * - current board is derived from clickedZones/fireballZones
     * - totalPoints = completedBoardsTotal + currentBoardTotal + sweetSpotBonus
     */
    private fun calculateCurrentPoints(participant: FireballParticipant?): PointsSnapshot {
        val p = participant ?: FireballParticipant("", "", "")
        val board = computeBoardBreakdown()

        val highest = maxOf(p.highestZone ?: 0, board.highestZone ?: 0).takeIf { it > 0 }
        val total = p.completedBoardsTotal + board.totalPoints + p.sweetSpotBonus

        return PointsSnapshot(
            // IMPORTANT: these are full totals for display/export (completed + current)
            nonFireballPoints = p.nonFireballPoints + board.nonFireballPoints,
            fireballPoints = p.fireballPoints + board.fireballPoints,
            totalPoints = total,
            highestZone = highest
        )
    }

    private fun finalizeCurrentParticipantForCompletion(raw: FireballParticipant): FireballParticipant {
        // Commit ONLY what React commits at the time the round ends:
        // - add the current board's nonFire/fire into participant nonFireballPoints/fireballPoints
        // - add current board total into completedBoardsTotal and increment completedBoards
        // - keep sweetSpotBonus as-is (persist)
        val board = computeBoardBreakdown()
        val highest = maxOf(raw.highestZone ?: 0, board.highestZone ?: 0).takeIf { it > 0 }

        val committed = raw.copy(
            nonFireballPoints = raw.nonFireballPoints + board.nonFireballPoints,
            fireballPoints = raw.fireballPoints + board.fireballPoints,
            completedBoards = raw.completedBoards + if (board.totalPoints > 0) 1 else 0,
            completedBoardsTotal = raw.completedBoardsTotal + board.totalPoints,
            highestZone = highest,
            totalPoints = (raw.completedBoardsTotal + board.totalPoints) + raw.sweetSpotBonus,
            allRollers = _allRollersActive.value
        )

        return committed
    }

    private fun recomputeScores(latestClickedValue: Int? = null) {
        val participant = _activeParticipant.value
        val board = computeBoardBreakdown()

        val sweetSpot = participant?.sweetSpotBonus ?: 0
        val completedBoardsTotal = participant?.completedBoardsTotal ?: 0

        // UI scores reflect completed boards + current board + sweet spot
        _currentBoardScore.value = board.totalPoints + sweetSpot
        _totalScore.value = completedBoardsTotal + board.totalPoints + sweetSpot

        // Update cumulative regular and fireball points (completed + current board)
        _currentBoardRegularPoints.value = (participant?.nonFireballPoints ?: 0) + board.nonFireballPoints
        _currentBoardFireballPoints.value = (participant?.fireballPoints ?: 0) + board.fireballPoints

        // IMPORTANT: do NOT mutate participant nonFireball/fireball/total/highest here.
        // Those are persisted totals and are committed only on board completion or Next().

        if (latestClickedValue != null) {
            val suffix = if (_isFireballActive.value) " (fireball)" else ""
            sidebarMessage.value = "+$latestClickedValue$suffix"
        }
    }

    fun resetGame() {
        logEvent("Reset Game")

        fun reset(p: FireballParticipant) = p.copy(
            nonFireballPoints = 0,
            fireballPoints = 0,
            totalPoints = 0,
            sweetSpotBonus = 0,
            highestZone = null,
            allRollers = false,
            completedBoards = 0,
            completedBoardsTotal = 0
        )

        _activeParticipant.value = _activeParticipant.value?.let(::reset)
        _participantsQueue.value = _participantsQueue.value.map(::reset)
        _completedParticipants.value = _completedParticipants.value.map(::reset)

        clearBoardInternal()
        recomputeScores()
        sidebarMessage.value = "Game reset"
        persistState()
    }

    fun toggleAllRollers() {
        _allRollersActive.value = !_allRollersActive.value
        logEvent("All Rollers: ${if (_allRollersActive.value) "ON" else "OFF"}")
        recomputeScores()
        persistState()
    }

    fun toggleFireball() {
        _isFireballActive.value = !_isFireballActive.value
        logEvent("Fireball Mode: ${if (_isFireballActive.value) "ON" else "OFF"}")

        // React: if sweet spot is on, bonus amount depends on fireball mode (±4 delta).
        if (_sweetSpotBonusAwarded.value) {
            val participant = _activeParticipant.value
            if (participant != null) {
                val delta = if (_isFireballActive.value) 4 else -4
                _activeParticipant.value = participant.copy(
                    sweetSpotBonus = (participant.sweetSpotBonus + delta).coerceAtLeast(0)
                )
            }
        }

        recomputeScores()
        persistState()
    }

    fun toggleManualSweetSpot() {
        logEvent("Sweet Spot toggled")
        val participant = _activeParticipant.value

        val turningOn = !_sweetSpotBonusAwarded.value
        val bonus = if (_isFireballActive.value) 8 else 4

        _sweetSpotBonusAwarded.value = turningOn

        if (participant != null) {
            _activeParticipant.value = if (turningOn) {
                participant.copy(sweetSpotBonus = participant.sweetSpotBonus + bonus)
            } else {
                participant.copy(sweetSpotBonus = (participant.sweetSpotBonus - bonus).coerceAtLeast(0))
            }
        }

        recomputeScores()
        persistState()
    }

    fun toggleFieldOrientation() {
        _isFieldFlipped.value = !_isFieldFlipped.value
        persistState()
    }

    // ---- Next/Skip + JSON export ----

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

    // Track timer metadata for export (React compatibility)
    private val _currentTimerFileName = MutableStateFlow<String?>(null)

    fun nextParticipant() {
        val current = _activeParticipant.value
        val next = _participantsQueue.value.firstOrNull()

        if (current == null && next == null) {
            // Create a blank participant and move to it
            _activeParticipant.value = FireballParticipant("", "", "")
            _currentParticipantLog.value = emptyList()
            clearBoardInternal()
            recomputeScores()
            persistState()
            return
        }

        // IMPORTANT: finalize scoring snapshot BEFORE clearing the board.
        val finalCurrent = current?.let { finalizeCurrentParticipantForCompletion(it) }

        if (finalCurrent != null) {
            // Persist finalized record so exports (which read model state) see real totals.
            _activeParticipant.value = finalCurrent
            _completedParticipants.value = _completedParticipants.value + finalCurrent

            // Emit per-round JSON export (React structure)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val stamp = fireballTimestamp(now)
            val safeHandler = finalCurrent.handler.replace("\\s+".toRegex(), "")
            val safeDog = finalCurrent.dog.replace("\\s+".toRegex(), "")
            val filename = "FireBall_${safeHandler}_${safeDog}_$stamp.json"

            // Timer metadata for export
            val timerFileName = _currentTimerFileName.value ?: "No Timer"
            val timerNumber = Regex("(\\d+)").find(timerFileName)?.value ?: "No Timer"

            val exportData = FireballRoundExport(
                gameMode = "FireBall",
                exportTimestamp = Clock.System.now().toString(),
                participantData = FireballParticipantData(
                    handler = finalCurrent.handler,
                    dog = finalCurrent.dog,
                    utn = finalCurrent.utn,
                    completedAt = now.toString()
                ),
                roundResults = FireballRoundResults(
                    nonFireballPoints = finalCurrent.nonFireballPoints,
                    fireballPoints = finalCurrent.fireballPoints,
                    totalPoints = finalCurrent.totalPoints,
                    sweetSpotBonus = finalCurrent.sweetSpotBonus,
                    highestZone = finalCurrent.highestZone,
                    allRollers = finalCurrent.allRollers,
                    timerNumber = timerNumber,
                    timerFileName = timerFileName,
                    clickedZones = _clickedZones.value.map { FireballGridPointDTO(it.row, it.col) },
                    fireBallZones = _fireballZones.value.map { FireballGridPointDTO(it.row, it.col) }
                ),
                roundLog = _currentParticipantLog.value
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )

            // Persist *before* we clear the board / advance.
            persistState()
        }

        // Reset per-participant timer metadata for next team
        _currentTimerFileName.value = "No Timer"

        // Advance
        _currentParticipantLog.value = emptyList()

        if (next == null) {
            _activeParticipant.value = FireballParticipant("", "", "")
            clearBoardInternal()
            recomputeScores()
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
        logEvent("Skip")
        val current = _activeParticipant.value ?: return
        val next = _participantsQueue.value.firstOrNull() ?: run {
            sidebarMessage.value = "No one to skip to"
            persistState()
            return
        }

        _participantsQueue.value = _participantsQueue.value.drop(1) + current
        _activeParticipant.value = next
        _currentParticipantLog.value = emptyList()

        clearBoardInternal()
        recomputeScores()
        persistState()
    }

    // ---- timer ----

    fun startRoundTimer(durationSeconds: Int = TIMER_DEFAULT_SECONDS) {
        // Pick a timer label deterministically for now. If you later add random sound timers,
        // set _currentTimerFileName accordingly.
        _currentTimerFileName.value = "No Timer"
        timerJob?.cancel()
        _timerSecondsRemaining.value = durationSeconds
        _isTimerRunning.value = true

        timerJob = scope.launch {
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
        // Keep whatever timer was used for this participant; if none, stay "No Timer".
        _isTimerRunning.value = false
        timerJob?.cancel()
        timerJob = null
        _timerSecondsRemaining.value = 0
    }

    fun resetRoundTimer() {
        _currentTimerFileName.value = "No Timer"
        stopRoundTimer()
    }

    fun openHelp() {
        sidebarMessage.value = "Tap a zone to score. Fireball Mode: tap a zone to make it Fireball (double points); tap again to clear while Fireball Mode is active. Sweet Spot adds +4 (or +8 if Fireball Mode is active)."
        persistState()
    }

    // ---- Export helpers ----

    suspend fun exportAsJson(): String {
        val state = FireballExportSnapshot(
            activeParticipant = _activeParticipant.value,
            queue = _participantsQueue.value,
            completed = _completedParticipants.value,
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
    val heightDivision: String = "",

    // React-style persisted totals
    val nonFireballPoints: Int = 0,
    val fireballPoints: Int = 0,
    val totalPoints: Int = 0,
    val sweetSpotBonus: Int = 0,
    val highestZone: Int? = null,
    val allRollers: Boolean = false,
    val completedBoards: Int = 0,
    val completedBoardsTotal: Int = 0
)

// Ensure the rest of the file refers to this stable top-level type.

@Serializable
private data class FireballRoundResults(
    val nonFireballPoints: Int,
    val fireballPoints: Int,
    val totalPoints: Int,
    val sweetSpotBonus: Int,
    val highestZone: Int?,
    val allRollers: Boolean,
    val timerNumber: String,
    val timerFileName: String,
    val clickedZones: List<FireballGridPointDTO>,
    val fireBallZones: List<FireballGridPointDTO>
)

@Serializable
private data class FireballParticipantData(
    val handler: String,
    val dog: String,
    val utn: String,
    val completedAt: String
)

@Serializable
private data class FireballRoundExport(
    val gameMode: String,
    val exportTimestamp: String,
    val participantData: FireballParticipantData,
    val roundResults: FireballRoundResults,
    val roundLog: List<String> = emptyList()
)

@Serializable
data class FireballPersistedState(
    val activeParticipant: FireballParticipant? = null,
    val queue: List<FireballParticipant> = emptyList(),
    val completed: List<FireballParticipant> = emptyList(),
    val currentParticipantLog: List<String> = emptyList(),
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
    val completed: List<FireballParticipant>,
    val allRollers: Boolean
)

private fun fireballTimestamp(now: kotlinx.datetime.LocalDateTime): String {
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
