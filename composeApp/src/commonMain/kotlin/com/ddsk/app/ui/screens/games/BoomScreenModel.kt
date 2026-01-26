package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Pretty JSON encoder for exports
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

enum class BoomScoringButton(val id: String, val label: String, val points: Int) {
    One("1", "1", 1),
    TwoA("2a", "2", 2),
    TwoB("2b", "2", 2),
    Five("5", "5", 5),
    Ten("10", "10", 10),
    Twenty("20", "20", 20),
    TwentyFive("25", "25", 25),
    ThirtyFive("35", "35", 35);

    companion object {
        private val byId = values().associateBy { it.id }
        private val unlockMap = mapOf(
            One to TwoA,
            TwoA to TwoB,
            TwoB to Five,
            Five to Ten,
            Ten to Twenty,
            Twenty to TwentyFive,
            TwentyFive to ThirtyFive
        )

        fun fromId(id: String): BoomScoringButton? = byId[id]
        fun unlockFor(button: BoomScoringButton): BoomScoringButton? = unlockMap[button]
    }
}

@Serializable
data class BoomParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val heightDivision: String = "",
    val stats: BoomParticipantStats = BoomParticipantStats()
)

@Serializable
data class BoomParticipantStats(
    val totalScore: Int = 0,
    val buttonCounts: Map<String, Int> = emptyMap(),
    val throwsCompleted: Int = 0,
    val duds: Int = 0,
    val sweetSpotAwards: Int = 0
) {
    companion object {
        fun fromScore(breakdown: BoomScoreBreakdown): BoomParticipantStats = BoomParticipantStats(
            totalScore = breakdown.totalScore,
            buttonCounts = breakdown.buttonCounts,
            throwsCompleted = breakdown.throwsCompleted,
            duds = breakdown.duds,
            sweetSpotAwards = breakdown.sweetSpotAwards
        )
    }
}

@Serializable
data class BoomScoreBreakdown(
    val totalScore: Int = 0,
    val buttonCounts: Map<String, Int> = emptyMap(),
    val throwsCompleted: Int = 0,
    val duds: Int = 0,
    val sweetSpotAwards: Int = 0,
    val lastThrowPoints: Int = 0
)

@Serializable
data class BoomButtonState(
    val clickedButtons: Set<String> = emptySet(),
    val enabledButtons: Set<String> = setOf(BoomScoringButton.One.id)
)

@Serializable
data class BoomUiState(
    val scoreBreakdown: BoomScoreBreakdown = BoomScoreBreakdown(),
    val buttonState: BoomButtonState = BoomButtonState(),
    val sweetSpotActive: Boolean = false,
    val allRollersActive: Boolean = false,
    val activeParticipant: BoomParticipant? = null,
    val queue: List<BoomParticipant> = emptyList(),
    val completedParticipants: List<BoomParticipant> = emptyList(),
    val isFieldFlipped: Boolean = false
)

class BoomScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    private val _uiState = MutableStateFlow(BoomUiState())
    val uiState = _uiState.asStateFlow()

    // Per-participant action log (like Greedy)
    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog = _currentParticipantLog.asStateFlow()

    private fun logEvent(message: String) {
        val entry = "${kotlinx.datetime.Clock.System.now()}: $message"
        _currentParticipantLog.value = _currentParticipantLog.value + entry
    }

    private var dataStore: DataStore? = null
    private val persistenceKey = "BoomData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val savedState = Json.decodeFromString<BoomUiState>(json)
                    _uiState.value = savedState
                } catch (e: Exception) {
                    // Ignore decode errors
                }
            } else {
                 if (_uiState.value.activeParticipant == null && _uiState.value.queue.isEmpty()) {
                    importSampleParticipants()
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

    private var timerJob: Job? = null
    private var _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()
    private val _timerRunning = MutableStateFlow(false)
    val timerRunning = _timerRunning.asStateFlow()

    // Flow for pending JSON exports
    private val _pendingJsonExport = MutableStateFlow<PendingExport?>(null)
    val pendingJsonExport = _pendingJsonExport.asStateFlow()

    data class PendingExport(
        val filename: String,
        val content: String
    )

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    // Undo functionality
    private val undoStack = mutableListOf<BoomUiState>()
    private val maxUndoLevels = 50

    private fun pushUndo() {
        val currentState = _uiState.value
        undoStack.add(0, currentState)
        if (undoStack.size > maxUndoLevels) {
            undoStack.removeAt(undoStack.size - 1)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(0)
            _uiState.value = previousState
            persistState()
        }
    }

    fun toggleAllRollers() {
        val newState = !_uiState.value.allRollersActive
        logEvent("All Rollers ${if (newState) "enabled" else "disabled"}")
        _uiState.update { it.copy(allRollersActive = newState) }
        persistState()
    }

    fun handleScoringButtonClick(button: BoomScoringButton) {
        pushUndo()
        logEvent("Button pressed: ${button.label}")
        _uiState.update { state ->
            if (button.id in state.buttonState.clickedButtons || button.id !in state.buttonState.enabledButtons) {
                return@update state
            }
            val unlocked = BoomScoringButton.unlockFor(button)?.id
            val newEnabled = buildSet {
                addAll(state.buttonState.enabledButtons - button.id)
                unlocked?.let { add(it) }
            }
            state.copy(
                buttonState = state.buttonState.copy(
                    clickedButtons = state.buttonState.clickedButtons + button.id,
                    enabledButtons = newEnabled
                )
            )
        }
        persistState()
    }

    fun handleBoom() {
        pushUndo()
        logEvent("Boom! button pressed")
        _uiState.update { state ->
            if (state.buttonState.clickedButtons.isEmpty()) return@update state
            val throwPoints = state.buttonState.clickedButtons.sumOf { BoomScoringButton.fromId(it)?.points ?: 0 }
            // Sweet spot bonus already added when toggled, so don't add it again
            val updatedCounts = state.scoreBreakdown.buttonCounts.toMutableMap()
            state.buttonState.clickedButtons.forEach { id ->
                updatedCounts[id] = (updatedCounts[id] ?: 0) + 1
            }
            state.copy(
                scoreBreakdown = state.scoreBreakdown.copy(
                    totalScore = state.scoreBreakdown.totalScore + throwPoints,
                    buttonCounts = updatedCounts,
                    throwsCompleted = state.scoreBreakdown.throwsCompleted + 1,
                    lastThrowPoints = throwPoints
                ),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun handleDud() {
        pushUndo()
        logEvent("Dud button pressed")
        _uiState.update { state ->
            val fiveClicked = state.buttonState.clickedButtons.contains(BoomScoringButton.Five.id)
            val dudScore = if (fiveClicked) 10 else 0
            val updatedCounts = state.scoreBreakdown.buttonCounts.toMutableMap()
            if (fiveClicked) {
                state.buttonState.clickedButtons.forEach { id ->
                    updatedCounts[id] = (updatedCounts[id] ?: 0) + 1
                }
            }
            state.copy(
                scoreBreakdown = state.scoreBreakdown.copy(
                    totalScore = state.scoreBreakdown.totalScore + dudScore,
                    buttonCounts = updatedCounts,
                    duds = state.scoreBreakdown.duds + 1,
                    lastThrowPoints = dudScore
                ),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun toggleSweetSpot() {
        pushUndo()
        _uiState.update { state ->
            val newSweetSpotActive = !state.sweetSpotActive
            logEvent("Sweet Spot ${if (newSweetSpotActive) "activated" else "deactivated"}")
            val scoreChange = if (newSweetSpotActive) 10 else -10
            val sweetSpotChange = if (newSweetSpotActive) 1 else -1

            state.copy(
                sweetSpotActive = newSweetSpotActive,
                scoreBreakdown = state.scoreBreakdown.copy(
                    totalScore = state.scoreBreakdown.totalScore + scoreChange,
                    sweetSpotAwards = state.scoreBreakdown.sweetSpotAwards + sweetSpotChange
                )
            )
        }
        persistState()
    }

    fun resetGame() {
        _uiState.update { state ->
            state.copy(
                scoreBreakdown = BoomScoreBreakdown(),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        // Clear the participant log for the next participant
        _currentParticipantLog.value = emptyList()
        persistState()
    }

    fun nextParticipant() {
        val currentState = _uiState.value
        val active = currentState.activeParticipant ?: return

        // Export JSON for current participant before moving to next
        val jsonContent = exportParticipantJson()

        // Generate filename
        val timestamp = kotlinx.datetime.Clock.System.now().toString()
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .substring(0, 15) // YYYYMMDDTHHMMSS
        val handlerName = active.handler.replace(Regex("\\s+"), "")
        val dogName = active.dog.replace(Regex("\\s+"), "")
        val filename = "Boom_${handlerName}_${dogName}_${timestamp}.json"

        // Emit the pending export
        _pendingJsonExport.value = PendingExport(filename, jsonContent)

        // Update state to move to next participant
        _uiState.update { state ->
            val finished = active.copy(stats = BoomParticipantStats.fromScore(state.scoreBreakdown))
            val nextActive = state.queue.firstOrNull()
            val remainingQueue = if (state.queue.isEmpty()) emptyList() else state.queue.drop(1)
            state.copy(
                completedParticipants = state.completedParticipants + finished,
                activeParticipant = nextActive,
                queue = remainingQueue,
                scoreBreakdown = BoomScoreBreakdown(),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun skipParticipant() {
        val active = _uiState.value.activeParticipant ?: return
        val queue = _uiState.value.queue
        val nextActive = queue.firstOrNull()
        val remainingQueue = if (queue.isEmpty()) emptyList() else queue.drop(1) + active
        _uiState.update {
            it.copy(
                activeParticipant = nextActive ?: active,
                queue = if (queue.isEmpty()) listOf(active) else remainingQueue
            )
        }
        resetGame() // persistState calls inside resetGame
    }

    fun previousParticipant() {
        val state = _uiState.value
        if (state.completedParticipants.isEmpty()) return

        // Get the last completed participant
        val previousParticipant = state.completedParticipants.last()
        val remainingCompleted = state.completedParticipants.dropLast(1)

        // Move current active to front of queue (if exists)
        val newQueue = if (state.activeParticipant != null) {
            listOf(state.activeParticipant!!) + state.queue
        } else {
            state.queue
        }

        _uiState.update {
            it.copy(
                activeParticipant = previousParticipant,
                queue = newQueue,
                completedParticipants = remainingCompleted,
                scoreBreakdown = BoomScoreBreakdown(
                    totalScore = previousParticipant.stats.totalScore,
                    buttonCounts = previousParticipant.stats.buttonCounts,
                    throwsCompleted = previousParticipant.stats.throwsCompleted,
                    duds = previousParticipant.stats.duds,
                    sweetSpotAwards = previousParticipant.stats.sweetSpotAwards,
                    lastThrowPoints = 0
                ),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String, addToExisting: Boolean = false) {
        val imported = parseCsv(csvText)
        val players = imported.map { BoomParticipant(it.handler, it.dog, it.utn, it.heightDivision) }
        applyImportedPlayers(players, addToExisting)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray, addToExisting: Boolean = false) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { BoomParticipant(it.handler, it.dog, it.utn, it.heightDivision) }
        applyImportedPlayers(players, addToExisting)
    }

    private fun applyImportedPlayers(players: List<BoomParticipant>, addToExisting: Boolean) {
        if (players.isEmpty()) return

        _uiState.update { state ->
            if (addToExisting) {
                // Add mode: append to existing queue
                if (state.activeParticipant == null) {
                    // No active participant, set first as active and rest as queue
                    state.copy(
                        activeParticipant = players.first(),
                        queue = players.drop(1)
                    )
                } else {
                    // Add all imported players to the end of the queue
                    state.copy(
                        queue = state.queue + players
                    )
                }
            } else {
                // Replace mode: clear and replace everything
                state.copy(
                    activeParticipant = players.first(),
                    queue = players.drop(1),
                    completedParticipants = emptyList()
                )
            }
        }

        if (!addToExisting) {
            resetGame() // Only reset game when replacing
        }
        persistState()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "handler,dog,utn,totalScore,throws,duds,sweetSpots\n"
        val participants = buildList {
            _uiState.value.activeParticipant?.let { add(it.copy(stats = BoomParticipantStats.fromScore(_uiState.value.scoreBreakdown))) }
            addAll(_uiState.value.queue.map { it.copy(stats = it.stats) })
            addAll(_uiState.value.completedParticipants)
        }
        return buildString {
            append(header)
            participants.forEach { participant ->
                val stats = participant.stats
                appendLine(listOf(
                    participant.handler,
                    participant.dog,
                    participant.utn,
                    stats.totalScore,
                    stats.throwsCompleted,
                    stats.duds,
                    stats.sweetSpotAwards
                ).joinToString(","))
            }
        }
    }

    fun startTimer(duration: Int) {
        timerJob?.cancel()
        _timeLeft.value = duration
        _timerRunning.value = true
        timerJob = scope.launch {
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

    fun toggleFieldOrientation() {
        _uiState.update { it.copy(isFieldFlipped = !it.isFieldFlipped) }
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String, heightDivision: String = "") {
        val newParticipant = BoomParticipant(handler, dog, utn, heightDivision)
        _uiState.update { state ->
            if (state.activeParticipant == null) {
                state.copy(activeParticipant = newParticipant)
            } else {
                state.copy(queue = state.queue + newParticipant)
            }
        }
        persistState()
    }

    fun clearParticipants() {
        _uiState.update { state ->
            state.copy(
                activeParticipant = null,
                queue = emptyList(),
                completedParticipants = emptyList(),
                scoreBreakdown = BoomScoreBreakdown(),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun exportLog(): String {
        val state = _uiState.value
        return buildString {
            appendLine("=== Boom Game Log ===")
            appendLine()

            state.activeParticipant?.let { active ->
                appendLine("Current Team:")
                appendLine("  ${active.handler} & ${active.dog} (${active.utn})")
                appendLine("  Score: ${state.scoreBreakdown.totalScore}")
                appendLine("  Throws: ${state.scoreBreakdown.throwsCompleted}")
                appendLine("  Duds: ${state.scoreBreakdown.duds}")
                appendLine("  Sweet Spots: ${state.scoreBreakdown.sweetSpotAwards}")
                appendLine()
            }

            if (state.queue.isNotEmpty()) {
                appendLine("Waiting (${state.queue.size}):")
                state.queue.forEach { participant ->
                    appendLine("  ${participant.handler} & ${participant.dog} (${participant.utn})")
                }
                appendLine()
            }

            if (state.completedParticipants.isNotEmpty()) {
                appendLine("Completed (${state.completedParticipants.size}):")
                state.completedParticipants.forEach { participant ->
                    appendLine("  ${participant.handler} & ${participant.dog} (${participant.utn})")
                    appendLine("    Score: ${participant.stats.totalScore}, Throws: ${participant.stats.throwsCompleted}, Duds: ${participant.stats.duds}, Sweet Spots: ${participant.stats.sweetSpotAwards}")
                }
            }
        }
    }

    fun exportParticipantJson(): String {
        val state = _uiState.value
        val active = state.activeParticipant ?: return "{}"
        val stats = BoomParticipantStats.fromScore(state.scoreBreakdown)

        // Get current timestamp in ISO format
        val timestamp = kotlinx.datetime.Clock.System.now().toString()

        return prettyJson.encodeToString(
            kotlinx.serialization.json.buildJsonObject {
                put("gameMode", kotlinx.serialization.json.JsonPrimitive("Boom"))
                put("exportTimestamp", kotlinx.serialization.json.JsonPrimitive(timestamp))

                putJsonObject("participantData") {
                    put("handler", kotlinx.serialization.json.JsonPrimitive(active.handler))
                    put("dog", kotlinx.serialization.json.JsonPrimitive(active.dog))
                    put("utn", kotlinx.serialization.json.JsonPrimitive(active.utn))
                    put("completedAt", kotlinx.serialization.json.JsonPrimitive(timestamp))
                }

                putJsonObject("roundResults") {
                    put("1", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["1"] ?: 0))
                    put("2a", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["2a"] ?: 0))
                    put("2b", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["2b"] ?: 0))
                    put("5", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["5"] ?: 0))
                    put("10", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["10"] ?: 0))
                    put("20", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["20"] ?: 0))
                    put("25", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["25"] ?: 0))
                    put("35", kotlinx.serialization.json.JsonPrimitive(stats.buttonCounts["35"] ?: 0))
                    put("sweetSpot", kotlinx.serialization.json.JsonPrimitive(if (stats.sweetSpotAwards > 0) "Yes" else "No"))
                    put("totalScore", kotlinx.serialization.json.JsonPrimitive(stats.totalScore))
                }

                // Round log with all button press actions
                putJsonArray("roundLog") {
                    _currentParticipantLog.value.forEach { logEntry ->
                        add(JsonPrimitive(logEntry))
                    }
                }
            }
        )
    }

    private fun importSampleParticipants() {
        val sample = listOf(
            BoomParticipant("Sample Handler", "Sample Dog", "UDC-000")
        )
        _uiState.update { it.copy(activeParticipant = sample.first(), queue = sample.drop(1)) }
    }
}
