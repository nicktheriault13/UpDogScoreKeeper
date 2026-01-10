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
        val participant = ThrowNGoParticipant(handler = handler.trim(), dog = dog.trim(), utn = utn.trim())
        _uiState.update { state ->
            if (state.activeParticipant == null) {
                state.copy(activeParticipant = participant)
            } else {
                state.copy(queue = state.queue + participant)
            }
        }
        persistState()
    }

    fun nextParticipant() {
        snapshotState()
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            val completedParticipant = active.copy(results = ThrowNGoRoundStats(state.scoreState))
            val next = state.queue.firstOrNull()
            val remaining = if (state.queue.isEmpty()) emptyList() else state.queue.drop(1)
            state.copy(
                completed = state.completed + completedParticipant,
                activeParticipant = next,
                queue = remaining,
                scoreState = ThrowNGoScoreState(),
                logEntries = state.logEntries + logLine("Saved result for ${active.displayName}")
            )
        }
        persistState()
    }

    fun previousParticipant() {
        snapshotState()
        _uiState.update { state ->
            val combined = buildList {
                state.activeParticipant?.let { add(it) }
                addAll(state.queue)
            }
            if (combined.isEmpty()) return@update state
            val newActive = combined.last()
            val newQueue = combined.dropLast(1)
            state.copy(activeParticipant = newActive, queue = newQueue)
        }
        persistState()
    }

    fun skipParticipant() {
        snapshotState()
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
            val newQueue = state.queue + active
            val next = newQueue.firstOrNull()
            val remaining = if (newQueue.size <= 1) emptyList() else newQueue.drop(1)
            state.copy(activeParticipant = next, queue = remaining)
        }
        persistState()
    }

    fun clearParticipants() {
        snapshotState()
        _uiState.value = ThrowNGoUiState()
        persistState()
    }

    fun importParticipants(csvData: String) {
        val lines = csvData.lines().filter { it.isNotBlank() }
        val header = lines.firstOrNull()?.lowercase() ?: return
        val dataLines = if (header.contains("handler")) lines.drop(1) else lines
        val participants = dataLines.mapNotNull { line ->
            val cols = line.split(",").map { it.trim() }
            if (cols.size < 2) return@mapNotNull null
            ThrowNGoParticipant(cols.first(), cols[1], cols.getOrElse(2) { "" })
        }
        applyImport(participants)
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val participants = imported.map { ThrowNGoParticipant(it.handler, it.dog, it.utn) }
        applyImport(participants)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val participants = imported.map { ThrowNGoParticipant(it.handler, it.dog, it.utn) }
        applyImport(participants)
    }

    private fun applyImport(participants: List<ThrowNGoParticipant>) {
        snapshotState()
        _uiState.update { state ->
            val first = participants.firstOrNull() ?: return@update state
            state.copy(
                activeParticipant = first,
                queue = participants.drop(1),
                completed = emptyList(),
                scoreState = ThrowNGoScoreState()
            )
        }
        persistState()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "handler,dog,utn,score,catches,bonus,misses,ob,sweetSpot,allRollers"
        val participants = buildList {
            _uiState.value.activeParticipant?.let { active ->
                add(active.copy(results = ThrowNGoRoundStats(_uiState.value.scoreState)))
            }
            addAll(_uiState.value.queue)
            addAll(_uiState.value.completed)
        }
        return buildString {
            appendLine(header)
            participants.forEach { participant ->
                val stats = participant.results ?: ThrowNGoRoundStats(ThrowNGoScoreState())
                appendLine(
                    listOf(
                        participant.handler,
                        participant.dog,
                        participant.utn,
                        stats.score,
                        stats.catches,
                        stats.bonusCatches,
                        stats.misses,
                        stats.ob,
                        stats.sweetSpot,
                        stats.allRollers
                    ).joinToString(",")
                )
            }
        }
    }

    fun exportLog(): String = _uiState.value.logEntries.joinToString(separator = "\n")

    fun startTimer() {
        timerJob?.cancel()
        _timeLeft.value = DEFAULT_TIMER_SECONDS
        _timerRunning.value = true
        timerJob = screenModelScope.launch {
            while (_timeLeft.value > 0 && _timerRunning.value) {
                delay(1000)
                _timeLeft.update { (it - 1).coerceAtLeast(0) }
            }
            _timerRunning.value = false
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = 0
    }

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
