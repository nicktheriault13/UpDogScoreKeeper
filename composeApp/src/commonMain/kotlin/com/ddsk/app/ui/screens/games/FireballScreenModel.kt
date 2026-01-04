package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FireballGridPoint(val row: Int, val col: Int)

data class FireballParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val stats: FireballParticipantStats = FireballParticipantStats()
)

data class FireballParticipantStats(
    val nonFireballPoints: Int = 0,
    val fireballPoints: Int = 0,
    val sweetSpotBonus: Int = 0,
    val totalPoints: Int = 0,
    val highestZone: Int? = null,
    val allRollers: Boolean = false,
    val completedBoards: Int = 0,
    val completedBoardsTotal: Int = 0
) {
    companion object {
        fun fromBreakdown(breakdown: FireballScoreBreakdown): FireballParticipantStats = FireballParticipantStats(
            nonFireballPoints = breakdown.nonFireballPoints,
            fireballPoints = breakdown.fireballPoints,
            sweetSpotBonus = breakdown.sweetSpotBonus,
            totalPoints = breakdown.totalPoints,
            highestZone = breakdown.highestZone,
            allRollers = breakdown.allRollers,
            completedBoards = breakdown.completedBoards,
            completedBoardsTotal = breakdown.completedBoardsTotal
        )
    }
}

data class FireballScoreBreakdown(
    val totalPoints: Int = 0,
    val nonFireballPoints: Int = 0,
    val fireballPoints: Int = 0,
    val sweetSpotBonus: Int = 0,
    val highestZone: Int? = null,
    val completedBoards: Int = 0,
    val completedBoardsTotal: Int = 0,
    val allRollers: Boolean = false
)

private data class BoardSnapshot(
    val nonFireballPoints: Int,
    val fireballPoints: Int,
    val highestZone: Int?
) {
    val total: Int
        get() = nonFireballPoints + fireballPoints
}

class FireballScreenModel : ScreenModel {

    private operator fun Set<FireballGridPoint>.plus(point: FireballGridPoint): Set<FireballGridPoint> =
        this.union(setOf(point))
    private operator fun Set<FireballGridPoint>.minus(point: FireballGridPoint): Set<FireballGridPoint> =
        this.subtract(setOf(point))

    companion object {
        val zoneValueGrid: List<List<Int?>> = listOf(
            listOf(8, 7, 5),
            listOf(6, 4, 2),
            listOf(3, 1, null)
        )

        fun zoneValue(row: Int, col: Int): Int? = zoneValueGrid
            .getOrNull(row)
            ?.getOrNull(col)
    }

    private val _totalScore = MutableStateFlow(0)
    val totalScore = _totalScore.asStateFlow()

    private val _clickedZones = MutableStateFlow(emptySet<FireballGridPoint>())
    val clickedZones = _clickedZones.asStateFlow()

    private val _fireballZones = MutableStateFlow(emptySet<FireballGridPoint>())
    val fireballZones = _fireballZones.asStateFlow()

    private val _isFireballActive = MutableStateFlow(false)
    val isFireballActive = _isFireballActive.asStateFlow()

    private val _sweetSpotBonusAwarded = MutableStateFlow(false)
    val sweetSpotBonusAwarded = _sweetSpotBonusAwarded.asStateFlow()

    private val _currentBoardScore = MutableStateFlow(0)
    val currentBoardScore = _currentBoardScore.asStateFlow()

    private val _isFieldFlipped = MutableStateFlow(false)
    val isFieldFlipped = _isFieldFlipped.asStateFlow()

    private val _completedBoards = MutableStateFlow<List<BoardSnapshot>>(emptyList())

    private val _allRollersActive = MutableStateFlow(false)
    val allRollersActive = _allRollersActive.asStateFlow()

    private val _scoreBreakdown = MutableStateFlow(FireballScoreBreakdown())
    val scoreBreakdown = _scoreBreakdown.asStateFlow()

    private val _activeParticipant = MutableStateFlow<FireballParticipant?>(null)
    val activeParticipant = _activeParticipant.asStateFlow()

    private val _participantsQueue = MutableStateFlow<List<FireballParticipant>>(emptyList())
    val participantsQueue = _participantsQueue.asStateFlow()

    private val _completedParticipants = MutableStateFlow<List<FireballParticipant>>(emptyList())
    val completedParticipants = _completedParticipants.asStateFlow()

    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed = _sidebarCollapsed.asStateFlow()

    private val _sidebarMessage = MutableStateFlow("")
    val sidebarMessage = _sidebarMessage.asStateFlow()

    private var countdownJob: Job? = null
    private val _timerSecondsRemaining = MutableStateFlow(0)
    val timerSecondsRemaining = _timerSecondsRemaining.asStateFlow()
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private val scoringPoints: Set<FireballGridPoint> = zoneValueGrid.flatMapIndexed { row, cols ->
        cols.mapIndexedNotNull { col, value -> value?.let { FireballGridPoint(row, col) } }
    }.toSet()

    init {
        if (_activeParticipant.value == null && _participantsQueue.value.isEmpty()) {
            importSampleParticipants()
        }
    }

    fun handleZoneClick(row: Int, col: Int) {
        val point = FireballGridPoint(row, col)
        val fireballModeActive = _isFireballActive.value

        if (point in _clickedZones.value || point in _fireballZones.value) {
            if (!fireballModeActive) {
                return
            }
            _clickedZones.value -= point
            _fireballZones.value -= point
        } else {
            if (fireballModeActive) {
                _fireballZones.value += point
            } else {
                _clickedZones.value += point
            }
        }
        finalizeBoardIfComplete()
        updateScores()
    }

    fun toggleFireball() {
        _isFireballActive.value = !_isFireballActive.value
        updateScores()
    }

    fun toggleManualSweetSpot() {
        _sweetSpotBonusAwarded.value = !_sweetSpotBonusAwarded.value
        updateScores()
    }

    fun toggleFieldOrientation() {
        _isFieldFlipped.value = !_isFieldFlipped.value
    }

    fun clearBoard() {
        resetBoardState(preserveAllRollers = true)
    }

    fun resetGame() {
        _completedBoards.value = emptyList()
        resetBoardState(preserveAllRollers = false)
    }

    fun toggleAllRollers() {
        _allRollersActive.value = !_allRollersActive.value
        updateScores()
    }

    fun nextParticipant() {
        val snapshot = currentParticipantSnapshot() ?: return
        _completedParticipants.value = _completedParticipants.value + snapshot
        promoteNextParticipant()
    }

    fun skipParticipant() {
        val active = _activeParticipant.value ?: return
        val queue = _participantsQueue.value
        val nextActive = queue.firstOrNull()
        val remainingQueue = if (queue.isEmpty()) emptyList() else queue.drop(1) + active
        _activeParticipant.value = nextActive ?: active
        _participantsQueue.value = if (queue.isEmpty()) listOf(active) else remainingQueue
        resetGame()
    }

    fun addParticipant(participant: FireballParticipant) {
        if (_activeParticipant.value == null) {
            _activeParticipant.value = participant
        } else {
            _participantsQueue.value = _participantsQueue.value + participant
        }
    }

    fun importParticipantsFromCsv(csvText: String) {
        val parsed = parseParticipantsCsv(csvText)
        if (parsed.isEmpty()) return
        _activeParticipant.value = parsed.first()
        _participantsQueue.value = parsed.drop(1)
        _completedParticipants.value = emptyList()
        resetGame()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "handler,dog,utn,totalPoints,nonFireballPoints,fireballPoints,sweetSpotBonus,highestZone,allRollers,completedBoards\n"
        return buildString {
            append(header)
            val queueSnapshots = _participantsQueue.value.mapIndexed { index, participant ->
                participant.copy(stats = if (index == 0 && _clickedZones.value.isNotEmpty()) FireballParticipantStats.fromBreakdown(_scoreBreakdown.value) else participant.stats)
            }
            val activeSnapshot = currentParticipantSnapshot()
            val completed = _completedParticipants.value
            val allRows = buildList {
                activeSnapshot?.let { add(it) }
                addAll(queueSnapshots)
                addAll(completed)
            }
            allRows.forEach { participant ->
                val stats = participant.stats
                appendLine(listOf(
                    participant.handler,
                    participant.dog,
                    participant.utn,
                    stats.totalPoints,
                    stats.nonFireballPoints,
                    stats.fireballPoints,
                    stats.sweetSpotBonus,
                    stats.highestZone ?: 0,
                    stats.allRollers,
                    stats.completedBoards
                ).joinToString(","))
            }
        }
    }

    private fun promoteNextParticipant() {
        val queue = _participantsQueue.value
        if (queue.isEmpty()) {
            _activeParticipant.value = null
            resetGame()
            return
        }
        _activeParticipant.value = queue.first()
        _participantsQueue.value = queue.drop(1)
        resetGame()
    }

    private fun finalizeBoardIfComplete() {
        val covered = _clickedZones.value + _fireballZones.value
        if (scoringPoints.all { point -> point in covered } && covered.isNotEmpty()) {
            val snapshot = calculateBoardSnapshot(_clickedZones.value, _fireballZones.value)
            _completedBoards.value = _completedBoards.value + snapshot
            _clickedZones.value = emptySet()
            _fireballZones.value = emptySet()
            _sweetSpotBonusAwarded.value = false
        }
    }

    private fun calculateBoardSnapshot(
        greens: Set<FireballGridPoint>,
        oranges: Set<FireballGridPoint>
    ): BoardSnapshot {
        var nonFireballPoints = 0
        var fireballPoints = 0
        var highestZone: Int? = null

        scoringPoints.forEach { point ->
            val value = getPointsForCell(point.row, point.col)
            if (point in oranges) {
                fireballPoints += value * 2
                highestZone = maxOf(highestZone ?: 0, value)
            } else if (point in greens) {
                nonFireballPoints += value
                highestZone = maxOf(highestZone ?: 0, value)
            }
        }
        return BoardSnapshot(nonFireballPoints, fireballPoints, highestZone)
    }

    private fun updateScores() {
        val currentSnapshot = calculateBoardSnapshot(_clickedZones.value, _fireballZones.value)
        val sweetSpotBonus = if (_sweetSpotBonusAwarded.value) {
            if (_isFireballActive.value) 8 else 4
        } else 0
        val completedNonFireball = _completedBoards.value.sumOf { it.nonFireballPoints }
        val completedFireball = _completedBoards.value.sumOf { it.fireballPoints }
        val completedTotals = _completedBoards.value.sumOf { it.total }
        val totalNonFireball = completedNonFireball + currentSnapshot.nonFireballPoints
        val totalFireball = completedFireball + currentSnapshot.fireballPoints
        val total = completedTotals + currentSnapshot.total + sweetSpotBonus
        val highestZone = (listOfNotNull(currentSnapshot.highestZone) + _completedBoards.value.mapNotNull { it.highestZone })
            .maxOrNull()
        _currentBoardScore.value = currentSnapshot.total + sweetSpotBonus
        _totalScore.value = total
        _scoreBreakdown.value = FireballScoreBreakdown(
            totalPoints = total,
            nonFireballPoints = totalNonFireball,
            fireballPoints = totalFireball,
            sweetSpotBonus = sweetSpotBonus,
            highestZone = highestZone,
            completedBoards = _completedBoards.value.size,
            completedBoardsTotal = completedTotals,
            allRollers = _allRollersActive.value
        )
    }

    private fun resetBoardState(preserveAllRollers: Boolean) {
        _clickedZones.value = emptySet()
        _fireballZones.value = emptySet()
        _sweetSpotBonusAwarded.value = false
        _isFireballActive.value = false
        _completedBoards.value = emptyList()
        if (!preserveAllRollers) {
            _allRollersActive.value = false
        }
        updateScores()
    }

    private fun currentParticipantSnapshot(): FireballParticipant? {
        val active = _activeParticipant.value ?: return null
        val stats = FireballParticipantStats.fromBreakdown(_scoreBreakdown.value)
        return active.copy(stats = stats)
    }

    private fun allParticipantsSnapshot(): List<FireballParticipant> {
        val snapshots = mutableListOf<FireballParticipant>()
        currentParticipantSnapshot()?.let { snapshots += it }
        snapshots += _participantsQueue.value
        snapshots += _completedParticipants.value
        return snapshots
    }

    private fun parseParticipantsCsv(csvText: String): List<FireballParticipant> {
        val lines = csvText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().contains("handler", ignoreCase = true)) lines.drop(1) else lines
        return dataLines.mapNotNull { line ->
            val cols = line.split(',').map { it.trim() }
            val handler = cols.getOrNull(0).orEmpty()
            val dog = cols.getOrNull(1).orEmpty()
            val utn = cols.getOrNull(2).orEmpty()
            if (handler.isBlank() && dog.isBlank()) return@mapNotNull null
            FireballParticipant(handler = handler, dog = dog, utn = utn)
        }
    }

    private fun getPointsForCell(row: Int, col: Int): Int = zoneValue(row, col) ?: 0

    fun startRoundTimer(durationSeconds: Int = 64) {
        countdownJob?.cancel()
        countdownJob = screenModelScope.launch {
            _isTimerRunning.value = true
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
        countdownJob?.cancel()
        countdownJob = null
    }

    fun resetRoundTimer() {
        stopRoundTimer()
        _timerSecondsRemaining.value = 0
    }

    // Public wrapper used by UI buttons - placeholder implementations
    fun importParticipants() {
        // For now, use the sample importer to populate participants when invoked from UI
        importSampleParticipants()
    }

    fun exportScores() {
        // Export current participants as CSV and log to console (UI may provide sharing in the future)
        val csv = exportParticipantsAsCsv()
        println("Fireball export CSV size: ${'$'}{csv.length}")
    }

    fun openHelp() {
        // Placeholder - UI will open its own help modal; keep method for compatibility
        println("Fireball help requested")
    }

    private fun importSampleParticipants() {
        val sample = listOf(
            FireballParticipant(handler = "Alex", dog = "Nova", utn = "UDC123"),
            FireballParticipant(handler = "Brooke", dog = "Pixel", utn = "UDC456"),
            FireballParticipant(handler = "Charlie", dog = "Rocket", utn = "UDC789")
        )
        if (sample.isEmpty()) {
            _activeParticipant.value = null
            _participantsQueue.value = emptyList()
        } else {
            _activeParticipant.value = sample.first()
            _participantsQueue.value = sample.drop(1)
        }
        _completedParticipants.value = emptyList()
        _sidebarMessage.value = "Sample participants loaded"
        resetGame()
    }

    fun clearParticipantsQueue() {
        _participantsQueue.value = emptyList()
        _activeParticipant.value = null
        _completedParticipants.value = emptyList()
        _sidebarMessage.value = "Participants cleared"
        resetGame()
    }

    fun toggleSidebar() {
        _sidebarCollapsed.value = !_sidebarCollapsed.value
        _sidebarMessage.value = if (_sidebarCollapsed.value) "Sidebar collapsed" else "Sidebar expanded"
    }
}
