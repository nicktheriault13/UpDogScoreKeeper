package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.ArrayDeque
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MAX_UNDO_LEVELS = 250
private const val DEFAULT_TIMER_SECONDS = 60

@Serializable
data class ThrowNGoRoundStats(
    val score: Int,
    val catches: Int,
    val bonusCatches: Int,
    val misses: Int,
    val ob: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean
)

private fun ThrowNGoRoundStats(scoreState: ThrowNGoScoreState): ThrowNGoRoundStats = ThrowNGoRoundStats(
    score = scoreState.score,
    catches = scoreState.catches,
    bonusCatches = scoreState.bonusCatches,
    misses = scoreState.misses,
    ob = scoreState.ob,
    sweetSpot = scoreState.sweetSpotActive,
    allRollers = scoreState.allRollersActive
)

@Serializable
data class ThrowNGoParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val heightDivision: String = "",
    val results: ThrowNGoRoundStats? = null
) {
    val displayName: String get() = buildString { // Changed to property getter to avoid serialization issues
        append(handler.ifBlank { "Handler" })
        if (dog.isNotBlank()) {
            append(" & ")
            append(dog)
        }
    }
}

@Serializable
data class ThrowNGoScoreState(
    val score: Int = 0,
    val catches: Int = 0,
    val bonusCatches: Int = 0,
    val misses: Int = 0,
    val ob: Int = 0,
    val sweetSpotActive: Boolean = false,
    val allRollersActive: Boolean = false
)

@Serializable
data class ThrowNGoUiState(
    val scoreState: ThrowNGoScoreState = ThrowNGoScoreState(),
    val activeParticipant: ThrowNGoParticipant? = null,
    val queue: List<ThrowNGoParticipant> = emptyList(),
    val completed: List<ThrowNGoParticipant> = emptyList(),
    val fieldFlipped: Boolean = false,
    val logEntries: List<String> = emptyList()
)

@Serializable
private data class ThrowNGoParticipantData(
    val handler: String,
    val dog: String,
    val utn: String,
    val completedAt: String
)

@Serializable
private data class ThrowNGoRoundResults(
    val catches: Int,
    val bonusCatches: Int,
    val misses: Int,
    val ob: Int,
    val score: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean
)

@Serializable
private data class ThrowNGoRoundExport(
    val gameMode: String,
    val exportTimestamp: String,
    val participantData: ThrowNGoParticipantData,
    val roundResults: ThrowNGoRoundResults,
    val roundLog: List<String> = emptyList()
)

class ThrowNGoScreenModel : ScreenModel {

    private val undoStack = ArrayDeque<ThrowNGoUiState>()

    private val _uiState = MutableStateFlow(ThrowNGoUiState())
    val uiState = _uiState.asStateFlow()

    private var dataStore: DataStore? = null
    private val persistenceKey = "ThrowNGoData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        screenModelScope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<ThrowNGoUiState>(json)
                    _uiState.value = saved
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                if (_uiState.value.activeParticipant == null) {
                    seedParticipants()
                }
            }
        }
    }

    private fun persistState() {
        val store = dataStore ?: return
        val state = _uiState.value
        screenModelScope.launch {
            try {
                val json = Json.encodeToString(state)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning = _timerRunning.asStateFlow()

    private val _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()

    private var timerJob: Job? = null

    private var logCounter = 0

    init {
        // seedParticipants() // Removed from init, moved to initPersistence
    }

    fun recordThrow(points: Int, isBonusRow: Boolean) {
        snapshotState()
        _uiState.update { state ->
            val newScore = state.scoreState.copy(
                score = state.scoreState.score + points,
                catches = state.scoreState.catches + 1,
                bonusCatches = state.scoreState.bonusCatches + if (isBonusRow) 1 else 0
            )
            state.copy(
                scoreState = newScore,
                logEntries = state.logEntries + logLine("Catch for +$points pts")
            )
        }
        persistState()
    }

    fun toggleSweetSpot() {
        snapshotState()
        _uiState.update { state ->
            val activating = !state.scoreState.sweetSpotActive
            val delta = if (activating) 5 else -5
            val updated = state.scoreState.copy(
                sweetSpotActive = activating,
                score = (state.scoreState.score + delta).coerceAtLeast(0)
            )
            state.copy(
                scoreState = updated,
                logEntries = state.logEntries + logLine(if (activating) "Sweet Spot ON" else "Sweet Spot OFF")
            )
        }
        persistState()
    }

    fun incrementMiss() {
        snapshotState()
        _uiState.update { state ->
            state.copy(
                scoreState = state.scoreState.copy(misses = state.scoreState.misses + 1),
                logEntries = state.logEntries + logLine("Miss recorded")
            )
        }
        persistState()
    }

    fun incrementOb() {
        snapshotState()
        _uiState.update { state ->
            val newScore = state.scoreState.copy(
                ob = state.scoreState.ob + 1,
                catches = state.scoreState.catches + 1
            )
            state.copy(
                scoreState = newScore,
                logEntries = state.logEntries + logLine("OB recorded")
            )
        }
        persistState()
    }

    fun toggleAllRollers() {
        snapshotState()
        _uiState.update { state ->
            val toggled = !state.scoreState.allRollersActive
            state.copy(
                scoreState = state.scoreState.copy(allRollersActive = toggled),
                logEntries = state.logEntries + logLine(if (toggled) "All Rollers enabled" else "All Rollers disabled")
            )
        }
        persistState()
    }

    fun resetRound() {
        snapshotState()
        _uiState.update { state ->
            state.copy(
                scoreState = ThrowNGoScoreState(),
                logEntries = state.logEntries + logLine("Round reset")
            )
        }
        persistState()
    }

    fun flipField() {
        _uiState.update { it.copy(fieldFlipped = !it.fieldFlipped) }
        persistState()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _uiState.value = previous
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        if (handler.isBlank() && dog.isBlank()) return
        snapshotState()
        val participant = ThrowNGoParticipant(handler = handler.trim(), dog = dog.trim(), utn = utn.trim(), heightDivision = "")

        _uiState.update { state ->
            if (state.activeParticipant == null) {
                state.copy(activeParticipant = participant)
            } else {
                state.copy(queue = state.queue + participant)
            }
        }
        persistState()
    }

    /**
     * Snapshot of participants with the active team's current round state applied.
     * This is what exports should read.
     */
    fun exportSnapshot(): List<ThrowNGoParticipant> {
        val state = _uiState.value
        val active = state.activeParticipant
        val activeWithResults = active?.copy(results = ThrowNGoRoundStats(state.scoreState))
        return buildList {
            if (activeWithResults != null) add(activeWithResults)
            addAll(state.queue)
            addAll(state.completed)
        }
    }

    fun exportScoresXlsx(templateBytes: ByteArray): ByteArray {
        return generateThrowNGoXlsx(exportSnapshot(), templateBytes)
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

    fun nextParticipant() {
        snapshotState()

        val currentState = _uiState.value
        val active = currentState.activeParticipant

        if (active == null && currentState.queue.isEmpty()) {
            // Create a blank participant and move to it
            _uiState.update { it.copy(activeParticipant = ThrowNGoParticipant("", "", ""), scoreState = ThrowNGoScoreState()) }
            persistState()
            return
        }

        // Finalize result BEFORE advancing/resetting
        val finalized = active?.copy(results = ThrowNGoRoundStats(currentState.scoreState))

        if (finalized != null) {
            // Emit per-round JSON export prompt
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val stamp = exportStamp(now)
            val safeHandler = finalized.handler.replace("\\s+".toRegex(), "")
            val safeDog = finalized.dog.replace("\\s+".toRegex(), "")
            val filename = "ThrowNGo_${safeHandler}_${safeDog}_$stamp.json"

            val exportData = ThrowNGoRoundExport(
                gameMode = "ThrowNGo",
                exportTimestamp = Clock.System.now().toString(),
                participantData = ThrowNGoParticipantData(
                    handler = finalized.handler,
                    dog = finalized.dog,
                    utn = finalized.utn,
                    completedAt = now.toString()
                ),
                roundResults = ThrowNGoRoundResults(
                    catches = finalized.results?.catches ?: 0,
                    bonusCatches = finalized.results?.bonusCatches ?: 0,
                    misses = finalized.results?.misses ?: 0,
                    ob = finalized.results?.ob ?: 0,
                    score = finalized.results?.score ?: 0,
                    sweetSpot = finalized.results?.sweetSpot ?: false,
                    allRollers = finalized.results?.allRollers ?: false
                ),
                roundLog = currentState.logEntries
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )
        }

        _uiState.update { state ->
            val activeNow = state.activeParticipant
            if (activeNow == null) return@update state

            val completedParticipant = activeNow.copy(results = ThrowNGoRoundStats(state.scoreState))
            val next = state.queue.firstOrNull()
            val remaining = if (state.queue.isEmpty()) emptyList() else state.queue.drop(1)

            state.copy(
                completed = state.completed + completedParticipant,
                activeParticipant = next ?: ThrowNGoParticipant("", "", ""),
                queue = remaining,
                scoreState = ThrowNGoScoreState(),
                logEntries = state.logEntries + logLine("Saved result for ${activeNow.displayName}")
            )
        }

        persistState()
    }

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    fun startTimer() {
        if (_timerRunning.value) return
        _timerRunning.value = true
        _timeLeft.value = DEFAULT_TIMER_SECONDS
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (_timerRunning.value && _timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value = (_timeLeft.value - 1).coerceAtLeast(0)
            }
            _timerRunning.value = false
        }
    }

    fun stopTimer() {
        _timerRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = DEFAULT_TIMER_SECONDS
    }

    fun previousParticipant() {
        snapshotState()
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            if (state.completed.isEmpty()) return@update state
            val prev = state.completed.last()
            val newCompleted = state.completed.dropLast(1)
            state.copy(
                activeParticipant = prev.copy(results = null),
                queue = listOf(active.copy(results = null)) + state.queue,
                completed = newCompleted,
                scoreState = ThrowNGoScoreState(),
                logEntries = state.logEntries + logLine("Previous participant")
            )
        }
        persistState()
    }

    fun skipParticipant() {
        snapshotState()
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            if (state.queue.isEmpty()) return@update state
            val next = state.queue.first()
            val remaining = state.queue.drop(1)
            state.copy(
                activeParticipant = next,
                queue = remaining + active,
                scoreState = ThrowNGoScoreState(),
                logEntries = state.logEntries + logLine("Skipped participant")
            )
        }
        persistState()
    }

    fun clearParticipants() {
        snapshotState()
        _uiState.value = ThrowNGoUiState(activeParticipant = ThrowNGoParticipant("", "", ""))
        persistState()
    }

    fun exportLog(): String = _uiState.value.logEntries.joinToString("\n")

    enum class ImportMode { ReplaceAll, Add }

    private fun applyImportedParticipants(players: List<ThrowNGoParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return
        snapshotState()
        _uiState.update { s ->
            when (mode) {
                ImportMode.ReplaceAll -> {
                    s.copy(
                        activeParticipant = players.first(),
                        queue = players.drop(1),
                        completed = emptyList(),
                        scoreState = ThrowNGoScoreState(),
                        logEntries = s.logEntries + logLine("Imported ${players.size} participants")
                    )
                }

                ImportMode.Add -> {
                    if (s.activeParticipant == null) {
                        s.copy(
                            activeParticipant = players.first(),
                            queue = players.drop(1),
                            logEntries = s.logEntries + logLine("Imported ${players.size} participants (add)")
                        )
                    } else {
                        s.copy(
                            queue = s.queue + players,
                            logEntries = s.logEntries + logLine("Imported ${players.size} participants (add)")
                        )
                    }
                }
            }
        }
        persistState()
    }

    fun importParticipantsFromCsv(contents: String, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseCsv(contents).map {
            ThrowNGoParticipant(
                handler = it.handler,
                dog = it.dog,
                utn = it.utn,
                heightDivision = it.heightDivision
            )
        }
        applyImportedParticipants(imported, mode)
    }

    fun importParticipantsFromXlsx(bytes: ByteArray, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseXlsx(bytes).map {
            ThrowNGoParticipant(
                handler = it.handler,
                dog = it.dog,
                utn = it.utn,
                heightDivision = it.heightDivision
            )
        }
        applyImportedParticipants(imported, mode)
    }

    // Backwards-compatible signatures expected by screens that call without mode
    // (No extra overloads needed; default parameter already covers this.)

    private fun snapshotState() {
        val snapshot = _uiState.value.deepCopy()
        undoStack.addLast(snapshot)
        if (undoStack.size > MAX_UNDO_LEVELS) {
            undoStack.removeFirst()
        }
    }

    private fun ThrowNGoUiState.deepCopy(): ThrowNGoUiState = copy(
        activeParticipant = activeParticipant?.copy(results = activeParticipant.results?.copy()),
        queue = queue.map { it.copy(results = it.results?.copy()) },
        completed = completed.map { it.copy(results = it.results?.copy()) },
        logEntries = logEntries.toList()
    )

    private fun logLine(message: String): String {
        // Fallback cross-platform log counter-based timestamp to avoid platform-specific time APIs in commonMain
        logCounter = (logCounter + 1) % 1_000_000
        val counterStr = logCounter.toString().padStart(6, '0')
        val participant = _uiState.value.activeParticipant?.displayName ?: "No team"
        // Replace String.format with string template
        return "[#$counterStr] $participant - $message"
    }

    private fun seedParticipants() {
        val sample = listOf(
            ThrowNGoParticipant("Alex", "Nova", "UDC-1001"),
            ThrowNGoParticipant("Brooke", "Pixel", "UDC-1002"),
            ThrowNGoParticipant("Charlie", "Rocket", "UDC-1003")
        )
        _uiState.value = ThrowNGoUiState(
            activeParticipant = sample.firstOrNull(),
            queue = sample.drop(1)
        )
    }
}
