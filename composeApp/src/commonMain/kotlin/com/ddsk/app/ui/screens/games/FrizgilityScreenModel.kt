package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlin.math.max
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Serializable
private data class FrizgilityRoundExport(
    val gameMode: String,
    val exportTimestamp: String,
    val participantData: FrizgilityParticipantData,
    val roundResults: FrizgilityRoundResults,
    val roundLog: List<String> = emptyList()
)

@Serializable
private data class FrizgilityParticipantData(
    val handler: String,
    val dog: String,
    val utn: String,
    val completedAt: String
)

@Serializable
private data class FrizgilityRoundResults(
    val obstacles: FrizgilityObstacles,
    val failures: FrizgilityFailures,
    val catches: FrizgilityCatches,
    val misses: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean,
    val finalScore: Int,
    val roundLog: List<String> = emptyList()
)

@Serializable
private data class FrizgilityObstacles(
    val obstacle1: Int,
    val obstacle2: Int,
    val obstacle3: Int
)

@Serializable
private data class FrizgilityFailures(
    val fail1: Int,
    val fail2: Int,
    val fail3: Int,
    val failTotal: Int
)

@Serializable
private data class FrizgilityCatches(
    val catch3to10: Int,
    val catch10plus: Int
)

class FrizgilityScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    private val undoStack = ArrayDeque<FrizgilityUiState>()
    private var timerJob: Job? = null
    private var logCounter = 0

    private val _uiState = MutableStateFlow(FrizgilityUiState())
    val uiState = _uiState.asStateFlow()

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning = _timerRunning.asStateFlow()
    private val _timeLeft = MutableStateFlow(DEFAULT_TIMER_SECONDS)
    val timeLeft = _timeLeft.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries = _logEntries.asStateFlow()

    data class PendingJsonExport(
        val filename: String,
        val content: String
    )

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport: StateFlow<PendingJsonExport?> = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    private var dataStore: DataStore? = null
    private val persistenceKey = "FrizgilityData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<FrizgilityUiState>(json)
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
        pushUndo()
        _uiState.update { state ->
            state.copy(sweetSpotActive = !state.sweetSpotActive)
        }
        appendLog("Sweet Spot toggled")
        persistState()
    }

    fun toggleAllRollers() {
        pushUndo()
        _uiState.update { state -> state.copy(allRollersEnabled = !state.allRollersEnabled) }
        appendLog("All Rollers toggled")
        persistState()
    }

    fun handleObstacleClick(lane: Int) {
        _uiState.update { state ->
            if (!state.obstaclePhaseActive || lane in state.laneLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (lane) {
                1 -> state.counters.copy(obstacle1 = state.counters.obstacle1 + 1)
                2 -> state.counters.copy(obstacle2 = state.counters.obstacle2 + 1)
                3 -> state.counters.copy(obstacle3 = state.counters.obstacle3 + 1)
                else -> state.counters
            }
            val newLocks = state.laneLocks + lane
            val nextState = state.copy(
                counters = updatedCounters,
                laneLocks = newLocks
            )
            if (newLocks.size == 3) {
                nextState.copy(
                    obstaclePhaseActive = false,
                    catchPhaseActive = true,
                    missClicksInPhase = 0
                )
            } else {
                nextState
            }
        }
        appendLog("Obstacle lane $lane pressed")
        persistState()
    }

    fun handleFailClick(lane: Int) {
        _uiState.update { state ->
            if (!state.obstaclePhaseActive || lane in state.laneLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (lane) {
                1 -> state.counters.copy(fail1 = state.counters.fail1 + 1)
                2 -> state.counters.copy(fail2 = state.counters.fail2 + 1)
                3 -> state.counters.copy(fail3 = state.counters.fail3 + 1)
                else -> state.counters
            }
            val newLocks = state.laneLocks + lane
            val nextState = state.copy(
                counters = updatedCounters,
                laneLocks = newLocks
            )
            if (newLocks.size == 3) {
                nextState.copy(
                    obstaclePhaseActive = false,
                    catchPhaseActive = true,
                    missClicksInPhase = 0
                )
            } else {
                nextState
            }
        }
        appendLog("Fail lane $lane pressed")
        persistState()
    }

    fun handleCatchClick(points: Int) {
        _uiState.update { state ->
            if (!state.catchPhaseActive || points in state.catchLocks) return@update state
            pushUndoInternal()
            val updatedCounters = when (points) {
                3 -> state.counters.copy(catch3to10 = state.counters.catch3to10 + 1)
                10 -> state.counters.copy(catch10plus = state.counters.catch10plus + 1)
                else -> state.counters
            }
            val updatedState = state.copy(
                counters = updatedCounters,
                catchLocks = state.catchLocks + points
            )
            updatedState.resetRound()
        }
        appendLog("Catch button ${if (points == 3) "3-10" else "10+"} pressed")
        persistState()
    }

    fun handleMissClick() {
        _uiState.update { state ->
            if (state.missClicksInPhase >= 3) return@update state
            pushUndoInternal()
            val updatedCounters = state.counters.copy(miss = state.counters.miss + 1)
            val newMissClicks = state.missClicksInPhase + 1
            val afterMiss = state.copy(
                counters = updatedCounters,
                missClicksInPhase = newMissClicks
            )
            when {
                newMissClicks >= 3 -> afterMiss.resetRound()
                state.catchPhaseActive && newMissClicks == 1 -> afterMiss.copy(
                    obstaclePhaseActive = true,
                    laneLocks = emptySet()
                )
                else -> afterMiss
            }
        }
        appendLog("Miss button pressed")
        persistState()
    }

    fun resetGame() {
        pushUndo()
        _uiState.update { state ->
            state.copy(
                counters = FrizgilityCounters(),
                laneLocks = emptySet(),
                catchLocks = emptySet(),
                obstaclePhaseActive = true,
                catchPhaseActive = false,
                missClicksInPhase = 0,
                sweetSpotActive = false
            )
        }
        _timeLeft.value = DEFAULT_TIMER_SECONDS
        stopTimer()
        appendLog("Game reset")
        persistState()
    }

    enum class ImportMode {
        Add,
        ReplaceAll
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode = ImportMode.Add) {
        val imported = parseCsv(csvText)
        val players = imported.map { FrizgilityParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players, mode)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray, mode: ImportMode = ImportMode.Add) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { FrizgilityParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players, mode)
    }

    private fun applyImportedPlayers(players: List<FrizgilityParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return
        _uiState.update { state ->
            when (mode) {
                ImportMode.Add -> {
                    state.copy(
                        participants = state.participants + players
                    )
                }
                ImportMode.ReplaceAll -> {
                    state.copy(
                        participants = players,
                        activeParticipantIndex = 0
                    )
                }
            }
        }
        if (mode == ImportMode.ReplaceAll) {
            resetGame()
        }
        appendLog("Imported ${players.size} participants (mode: $mode)")
        persistState()
    }

    fun exportParticipantsSnapshot(): String {
        // ... implementation existing or placeholder ...
        // Implementing basic CSV format return
        val participants = _uiState.value.participants.toMutableList()
        _uiState.value.activeParticipant?.let { participants.add(0, it) }
        return participants.joinToString("\n") { "${it.handler},${it.dog},${it.utn}" }
    }

    fun exportLog(): String {
        val entries = _logEntries.value
        return if (entries.isEmpty()) {
            "No log entries"
        } else {
            entries.reversed().joinToString("\n")
        }
    }

    data class ExportResult(
        val xlsxBytes: ByteArray,
        val tieWarning: String? = null
    )

    fun exportScoresXlsx(templateBytes: ByteArray): ByteArray {
        val currentState = _uiState.value
        val participants = mutableListOf<FrizgilityParticipantWithResults>()

        // Add all completed participants
        participants.addAll(currentState.completedParticipants)

        // Add active participant with current scores if they have any scoring data
        val active = currentState.activeParticipant
        if (active != null) {
            val hasScores = currentState.counters.obstacle1 + currentState.counters.obstacle2 +
                           currentState.counters.obstacle3 + currentState.counters.catch10plus +
                           currentState.counters.catch3to10 + currentState.counters.miss > 0 ||
                           currentState.sweetSpotActive

            if (hasScores) {
                participants.add(
                    FrizgilityParticipantWithResults(
                        handler = active.handler,
                        dog = active.dog,
                        utn = active.utn,
                        obstacle1 = currentState.counters.obstacle1,
                        obstacle2 = currentState.counters.obstacle2,
                        obstacle3 = currentState.counters.obstacle3,
                        fail1 = currentState.counters.fail1,
                        fail2 = currentState.counters.fail2,
                        fail3 = currentState.counters.fail3,
                        catch10plus = currentState.counters.catch10plus,
                        catch3to10 = currentState.counters.catch3to10,
                        misses = currentState.counters.miss,
                        sweetSpot = currentState.sweetSpotActive,
                        heightDivision = active.heightDivision
                    )
                )
            }
        }

        return generateFrizgilityXlsx(participants, templateBytes)
    }

    fun checkForTies(participants: List<FrizgilityParticipantWithResults>): String? {
        if (participants.isEmpty()) return null

        // Calculate scores and sort
        data class ParticipantScore(
            val participant: FrizgilityParticipantWithResults,
            val finalScore: Int,
            val misses: Int,
            val failTotal: Int
        )

        val scored = participants.map { p ->
            val failTotal = p.fail1 + p.fail2 + p.fail3
            val finalScore = (p.obstacle1 + p.obstacle2 + p.obstacle3) * 5 +
                           (p.catch10plus * 10) + (p.catch3to10 * 3) +
                           (if (p.sweetSpot) 10 else 0)
            ParticipantScore(p, finalScore, p.misses, failTotal)
        }.sortedWith(
            compareByDescending<ParticipantScore> { it.finalScore }
                .thenBy { it.misses }
                .thenBy { it.failTotal }
        )

        // Check for unresolved ties in top 5 positions
        val ties = mutableListOf<Pair<Int, List<String>>>()
        var i = 0
        while (i < minOf(5, scored.size)) {
            val current = scored[i]
            val tiedTeams = mutableListOf<String>()
            val place = i + 1

            var j = i
            while (j < scored.size) {
                val participant = scored[j]
                if (participant.finalScore == current.finalScore &&
                    participant.misses == current.misses &&
                    participant.failTotal == current.failTotal) {
                    val teamName = "${participant.participant.handler} & ${participant.participant.dog}"
                    tiedTeams.add(teamName)
                    j++
                } else {
                    break
                }
            }

            if (tiedTeams.size > 1) {
                ties.add(Pair(place, tiedTeams))
            }
            i = j
        }

        if (ties.isEmpty()) return null

        val tieMessages = ties.joinToString("\n") { (place, teams) ->
            val placeStr = when (place) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                4 -> "4th"
                5 -> "5th"
                else -> "${place}th"
            }
            "$placeStr place: ${teams.joinToString(", ")}"
        }

        return "The following teams are tied and could not be resolved by tiebreakers:\n\n$tieMessages"
    }

    private fun exportSnapshot(): List<FrizgilityParticipant> {
        val current = _uiState.value
        val all = mutableListOf<FrizgilityParticipant>()
        current.activeParticipant?.let { all.add(it) }
        all.addAll(current.participants)
        return all
    }

    fun nextParticipant() {
        if (_uiState.value.participants.isNotEmpty()) {
            val currentState = _uiState.value
            val currentParticipant = currentState.activeParticipant

            // Save the current participant's results and emit JSON export
            if (currentParticipant != null) {
                val completedParticipant = FrizgilityParticipantWithResults(
                    handler = currentParticipant.handler,
                    dog = currentParticipant.dog,
                    utn = currentParticipant.utn,
                    obstacle1 = currentState.counters.obstacle1,
                    obstacle2 = currentState.counters.obstacle2,
                    obstacle3 = currentState.counters.obstacle3,
                    fail1 = currentState.counters.fail1,
                    fail2 = currentState.counters.fail2,
                    fail3 = currentState.counters.fail3,
                    catch10plus = currentState.counters.catch10plus,
                    catch3to10 = currentState.counters.catch3to10,
                    misses = currentState.counters.miss,
                    sweetSpot = currentState.sweetSpotActive,
                    heightDivision = currentParticipant.heightDivision
                )

                // Emit per-round JSON export
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val stamp = exportStamp(now)
                val safeHandler = currentParticipant.handler.replace("\\s+".toRegex(), "")
                val safeDog = currentParticipant.dog.replace("\\s+".toRegex(), "")
                val filename = "Frizgility_${safeHandler}_${safeDog}_$stamp.json"

                val failTotal = currentState.counters.fail1 + currentState.counters.fail2 + currentState.counters.fail3
                val finalScore = (currentState.counters.obstacle1 + currentState.counters.obstacle2 + currentState.counters.obstacle3) * 5 +
                                (currentState.counters.catch10plus * 10) + (currentState.counters.catch3to10 * 3) +
                                (if (currentState.sweetSpotActive) 10 else 0)

                val exportData = FrizgilityRoundExport(
                    gameMode = "Frizgility",
                    exportTimestamp = Clock.System.now().toString(),
                    participantData = FrizgilityParticipantData(
                        handler = currentParticipant.handler,
                        dog = currentParticipant.dog,
                        utn = currentParticipant.utn,
                        completedAt = now.toString()
                    ),
                    roundResults = FrizgilityRoundResults(
                        obstacles = FrizgilityObstacles(
                            obstacle1 = currentState.counters.obstacle1,
                            obstacle2 = currentState.counters.obstacle2,
                            obstacle3 = currentState.counters.obstacle3
                        ),
                        failures = FrizgilityFailures(
                            fail1 = currentState.counters.fail1,
                            fail2 = currentState.counters.fail2,
                            fail3 = currentState.counters.fail3,
                            failTotal = failTotal
                        ),
                        catches = FrizgilityCatches(
                            catch3to10 = currentState.counters.catch3to10,
                            catch10plus = currentState.counters.catch10plus
                        ),
                        misses = currentState.counters.miss,
                        sweetSpot = currentState.sweetSpotActive,
                        allRollers = currentState.allRollersEnabled,
                        finalScore = finalScore,
                        roundLog = _logEntries.value
                    ),
                    roundLog = _logEntries.value
                )

                _pendingJsonExport.value = PendingJsonExport(
                    filename = filename,
                    content = exportJson.encodeToString(exportData)
                )

                _uiState.update {
                    it.copy(
                        activeParticipantIndex = (it.activeParticipantIndex + 1) % it.participants.size,
                        completedParticipants = it.completedParticipants + completedParticipant
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        activeParticipantIndex = (it.activeParticipantIndex + 1) % it.participants.size
                    )
                }
            }

            resetGame()
            persistState()
        }
    }

    fun previousParticipant() {
        if (_uiState.value.participants.isNotEmpty()) {
            val currentIndex = _uiState.value.activeParticipantIndex
            val participantsSize = _uiState.value.participants.size
            val newIndex = if (currentIndex == 0) participantsSize - 1 else currentIndex - 1
            _uiState.update {
                it.copy(activeParticipantIndex = newIndex)
            }
            resetGame()
            persistState()
        }
    }

    fun skipParticipant() {
        nextParticipant() // For now skip just moves next
    }

    fun flipField() {
        _uiState.update { it.copy(fieldFlipped = !it.fieldFlipped) }
        appendLog("Field flipped")
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val newParticipant = FrizgilityParticipant(handler, dog, utn)
        _uiState.update { it.copy(participants = it.participants + newParticipant) }
        persistState()
    }

    fun clearParticipants() {
        _uiState.update {
            it.copy(
                participants = emptyList(),
                activeParticipantIndex = 0,
                completedParticipants = emptyList()
            )
        }
        persistState()
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
        persistState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeFirst()
        _uiState.value = previous
        appendLog("Undo applied")
        persistState()
    }

    fun startTimer(durationSeconds: Int = DEFAULT_TIMER_SECONDS) {
        if (_timerRunning.value) return
        _timeLeft.value = durationSeconds
        _timerRunning.value = true
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value = max(0, _timeLeft.value - 1)
            }
            _timerRunning.value = false
        }
        appendLog("Timer started")
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        appendLog("Timer stopped")
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = DEFAULT_TIMER_SECONDS
    }

    private fun FrizgilityUiState.resetRound(): FrizgilityUiState = copy(
        counters = counters,
        laneLocks = emptySet(),
        catchLocks = emptySet(),
        obstaclePhaseActive = true,
        catchPhaseActive = false,
        missClicksInPhase = 0
    ).clearCountsIfNeeded()

    private fun FrizgilityUiState.clearCountsIfNeeded(): FrizgilityUiState = copy(
        counters = counters.copy(),
        sweetSpotActive = sweetSpotActive
    )

    private fun pushUndo() {
        undoStack.addFirst(_uiState.value)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeLast()
        }
    }

    private fun pushUndoInternal() {
        undoStack.addFirst(_uiState.value)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeLast()
        }
    }

    private fun appendLog(message: String) {
        logCounter += 1
        val entry = "[${logCounter.toString().padStart(3, '0')}] $message"
        _logEntries.update { listOf(entry) + it }
        // Note: logs are currently not persisted in state, but could be
    }

    companion object {
        private const val DEFAULT_TIMER_SECONDS = 60
        private const val MAX_UNDO_SIZE = 200
    }

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun exportStamp(now: kotlinx.datetime.LocalDateTime): String {
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
}

@Serializable
data class FrizgilityParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val heightDivision: String = ""
)

@Serializable
data class FrizgilityParticipantWithResults(
    val handler: String,
    val dog: String,
    val utn: String,
    val obstacle1: Int = 0,
    val obstacle2: Int = 0,
    val obstacle3: Int = 0,
    val fail1: Int = 0,
    val fail2: Int = 0,
    val fail3: Int = 0,
    val catch10plus: Int = 0,
    val catch3to10: Int = 0,
    val misses: Int = 0,
    val sweetSpot: Boolean = false,
    val heightDivision: String = ""
)

@Serializable
data class FrizgilityCounters(
    val obstacle1: Int = 0,
    val obstacle2: Int = 0,
    val obstacle3: Int = 0,
    val fail1: Int = 0,
    val fail2: Int = 0,
    val fail3: Int = 0,
    val catch3to10: Int = 0,
    val catch10plus: Int = 0,
    val miss: Int = 0
)

@Serializable
data class ScoreBreakdown(
    val obstacle1: Int,
    val obstacle2: Int,
    val obstacle3: Int,
    val fail1: Int,
    val fail2: Int,
    val fail3: Int,
    val catch3to10: Int,
    val catch10plus: Int,
    val miss: Int,
    val sweetSpotActive: Boolean,
    val totalScore: Int
)

@Serializable
data class FrizgilityUiState(
    val counters: FrizgilityCounters = FrizgilityCounters(),
    val sweetSpotActive: Boolean = false,
    val allRollersEnabled: Boolean = false,
    val obstaclePhaseActive: Boolean = true,
    val catchPhaseActive: Boolean = false,
    val laneLocks: Set<Int> = emptySet(),
    val catchLocks: Set<Int> = emptySet(),
    val missClicksInPhase: Int = 0,
    val sidebarCollapsed: Boolean = false,
    val fieldFlipped: Boolean = false,
    val participants: List<FrizgilityParticipant> = listOf(
        FrizgilityParticipant("Alex", "Nova", "UTN-001"),
        FrizgilityParticipant("Blair", "Zelda", "UTN-002"),
        FrizgilityParticipant("Casey", "Milo", "UTN-003")
    ),
    val activeParticipantIndex: Int = 0,
    val completedParticipants: List<FrizgilityParticipantWithResults> = emptyList()
) {
    val scoreBreakdown: ScoreBreakdown get() = counters.toScoreBreakdown(sweetSpotActive)
    val activeParticipant: FrizgilityParticipant?
        get() = participants.getOrNull(activeParticipantIndex)
    val queue: List<FrizgilityParticipant>
        get() = if (participants.size <= 1) emptyList() else participants.drop(activeParticipantIndex + 1)
}

private fun FrizgilityCounters.toScoreBreakdown(sweetSpotActive: Boolean): ScoreBreakdown {
    val obstacleScore = (obstacle1 + obstacle2 + obstacle3) * 5
    val catchScore = (catch3to10 * 3) + (catch10plus * 10)
    val sweetSpotScore = if (sweetSpotActive) 10 else 0
    val total = obstacleScore + catchScore + sweetSpotScore
    return ScoreBreakdown(
        obstacle1,
        obstacle2,
        obstacle3,
        fail1,
        fail2,
        fail3,
        catch3to10,
        catch10plus,
        miss,
        sweetSpotActive,
        total
    )
}
