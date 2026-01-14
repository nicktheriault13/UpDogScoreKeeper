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

    fun handleScoringButtonClick(button: BoomScoringButton) {
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
        _uiState.update { state ->
            if (state.buttonState.clickedButtons.isEmpty()) return@update state
            val throwPoints = state.buttonState.clickedButtons.sumOf { BoomScoringButton.fromId(it)?.points ?: 0 }
            val sweetBonus = if (state.sweetSpotActive) 10 else 0
            val updatedCounts = state.scoreBreakdown.buttonCounts.toMutableMap()
            state.buttonState.clickedButtons.forEach { id ->
                updatedCounts[id] = (updatedCounts[id] ?: 0) + 1
            }
            state.copy(
                scoreBreakdown = state.scoreBreakdown.copy(
                    totalScore = state.scoreBreakdown.totalScore + throwPoints + sweetBonus,
                    buttonCounts = updatedCounts,
                    throwsCompleted = state.scoreBreakdown.throwsCompleted + 1,
                    sweetSpotAwards = state.scoreBreakdown.sweetSpotAwards + if (sweetBonus > 0) 1 else 0,
                    lastThrowPoints = throwPoints + sweetBonus
                ),
                buttonState = BoomButtonState(),
                sweetSpotActive = false
            )
        }
        persistState()
    }

    fun handleDud() {
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
        _uiState.update { it.copy(sweetSpotActive = !it.sweetSpotActive) }
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
        persistState()
    }

    fun nextParticipant() {
        _uiState.update { state ->
            val active = state.activeParticipant ?: return@update state
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

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val players = imported.map { BoomParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { BoomParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    private fun applyImportedPlayers(players: List<BoomParticipant>) {
        if (players.isEmpty()) return
        _uiState.update {
            it.copy(
                activeParticipant = players.first(),
                queue = players.drop(1),
                completedParticipants = emptyList()
            )
        }
        resetGame() // persistState calls inside resetGame
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

    private fun importSampleParticipants() {
        val sample = listOf(
            BoomParticipant("Sample Handler", "Sample Dog", "UDC-000")
        )
        _uiState.update { it.copy(activeParticipant = sample.first(), queue = sample.drop(1)) }
    }
}
