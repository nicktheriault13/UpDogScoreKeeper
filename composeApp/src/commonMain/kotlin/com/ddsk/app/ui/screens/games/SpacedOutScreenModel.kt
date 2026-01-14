package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import com.ddsk.app.persistence.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SpacedOutZone(val label: String) {
    Zone1("1"), Zone2("2"), Zone3("3"), Zone4("4"), Zone5("5"),
    Zone6("6"), Zone7("7"), Zone8("8"), Zone9("9"), Zone10("10"),
    Zone11("11"), Zone12("12"), Zone13("13"), Zone14("14"), Zone15("15")
}

@Serializable
data class SpacedOutParticipant(
    val handler: String,
    val dog: String,
    val utn: String
) {
    fun displayName(): String = buildString {
        if (handler.isNotBlank()) append(handler.trim())
        if (dog.isNotBlank()) {
            if (isNotEmpty()) append(" & ")
            append(dog.trim())
        }
    }.ifBlank { handler.ifBlank { dog.ifBlank { "Unknown Team" } } }
}

@Serializable
data class SpacedOutRoundResult(
    val participant: SpacedOutParticipant,
    val score: Int,
    val spacedOutCount: Int,
    val zonesCaught: Int,
    val misses: Int,
    val ob: Int,
    val sweetSpotBonus: Boolean
)

@Serializable
data class SpacedOutUiState(
    val activeParticipant: SpacedOutParticipant? = null,
    val queue: List<SpacedOutParticipant> = emptyList(),
    val completed: List<SpacedOutRoundResult> = emptyList(),
    val score: Int = 0,
    val spacedOutCount: Int = 0,
    val zonesCaught: Int = 0,
    val misses: Int = 0,
    val ob: Int = 0,
    val sweetSpotBonus: Boolean = false,
    val fieldFlipped: Boolean = false,
    val clickedZones: Set<SpacedOutZone> = emptySet(),
    val logEntries: List<String> = emptyList()
)

class SpacedOutScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    // Timer State
    private val _timeLeft = MutableStateFlow(60)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()
    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()
    private var timerJob: Job? = null

    private var logCounter = 0

    private val zonePoints = mapOf(
        SpacedOutZone.Zone1 to 1,
        SpacedOutZone.Zone2 to 1,
        SpacedOutZone.Zone3 to 1,
        SpacedOutZone.Zone4 to 1,
        SpacedOutZone.Zone5 to 1,
        SpacedOutZone.Zone6 to 1,
        SpacedOutZone.Zone7 to 1,
        SpacedOutZone.Zone8 to 1,
        SpacedOutZone.Zone9 to 1,
        SpacedOutZone.Zone10 to 1,
        SpacedOutZone.Zone11 to 1,
        SpacedOutZone.Zone12 to 1,
        SpacedOutZone.Zone13 to 1,
        SpacedOutZone.Zone14 to 1,
        SpacedOutZone.Zone15 to 1
    )

    private val _uiState = MutableStateFlow(SpacedOutUiState())

    // Derived Flows for UI compatibility
    val score: StateFlow<Int> = _uiState.map { it.score }.stateIn(scope, SharingStarted.Eagerly, 0)
    val spacedOutCount: StateFlow<Int> = _uiState.map { it.spacedOutCount }.stateIn(scope, SharingStarted.Eagerly, 0)
    val zonesCaught: StateFlow<Int> = _uiState.map { it.zonesCaught }.stateIn(scope, SharingStarted.Eagerly, 0)
    val misses: StateFlow<Int> = _uiState.map { it.misses }.stateIn(scope, SharingStarted.Eagerly, 0)
    val ob: StateFlow<Int> = _uiState.map { it.ob }.stateIn(scope, SharingStarted.Eagerly, 0)
    val sweetSpotBonusOn: StateFlow<Boolean> = _uiState.map { it.sweetSpotBonus }.stateIn(scope, SharingStarted.Eagerly, false)
    val fieldFlipped: StateFlow<Boolean> = _uiState.map { it.fieldFlipped }.stateIn(scope, SharingStarted.Eagerly, false)
    val clickedZonesInRound: StateFlow<Set<SpacedOutZone>> = _uiState.map { it.clickedZones }.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val activeParticipant: StateFlow<SpacedOutParticipant?> = _uiState.map { it.activeParticipant }.stateIn(scope, SharingStarted.Eagerly, null)
    val participantQueue: StateFlow<List<SpacedOutParticipant>> = _uiState.map { it.queue }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val logEntries: StateFlow<List<String>> = _uiState.map { it.logEntries }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var dataStore: DataStore? = null
    private val persistenceKey = "SpacedOutData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<SpacedOutUiState>(json)
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

    fun handleZoneClick(zone: SpacedOutZone) {
        if (_uiState.value.activeParticipant == null) return

        val currentScore = _uiState.value.score
        val points = zonePoints[zone] ?: return
        val newScore = currentScore + points

        _uiState.update { currentState ->
            val zonesCaught = currentState.zonesCaught + 1
            val clickedZones = currentState.clickedZones + zone
            val allZonesCaught = clickedZones.size == zonePoints.size

            // Logic for Spaced Out Bonus? (e.g. all 15 zones)

            var spacedOutCount = currentState.spacedOutCount
            if (allZonesCaught && currentState.clickedZones.size < zonePoints.size) { // Just completed
                 spacedOutCount += 1
                 // Reset clicked zones? Or keep them? Usually "Spaced Out" means clear board and bonus.
                 // Assuming Spaced Out clears clicked zones for next round.
                 // If not, logic differs. Assuming reset based on "Spaced Out" name.
            }

            currentState.copy(
                score = newScore,
                zonesCaught = zonesCaught,
                spacedOutCount = spacedOutCount,
                clickedZones = if (allZonesCaught) emptySet() else clickedZones
            )
        }
        if (_uiState.value.clickedZones.isEmpty() && _uiState.value.zonesCaught > 0) {
             // Spaced Out triggered reset above?
             appendLog("Spaced Out! Board Cleared.") // Example
        }
        persistState()
    }

    fun toggleSweetSpotBonus() {
        _uiState.update { currentState ->
            val newScore = if (!currentState.sweetSpotBonus) {
                currentState.score + SWEET_SPOT_BONUS_POINTS
            } else {
                (currentState.score - SWEET_SPOT_BONUS_POINTS).coerceAtLeast(0)
            }
            currentState.copy(
                score = newScore,
                sweetSpotBonus = !currentState.sweetSpotBonus
            )
        }
        persistState()
    }

    fun incrementMisses() {
        _uiState.update { currentState ->
            val newMisses = currentState.misses + 1
            currentState.copy(misses = newMisses)
        }
        appendLog("Miss recorded")
        persistState()
    }

    fun incrementOb() {
        _uiState.update { currentState ->
            val newOb = currentState.ob + 1
            currentState.copy(ob = newOb)
        }
        appendLog("OB recorded")
        persistState()
    }

    fun flipField() {
        _uiState.update { currentState ->
            val newFlippedState = !currentState.fieldFlipped
            currentState.copy(fieldFlipped = newFlippedState)
        }
        appendLog("Field flipped")
        persistState()
    }

    fun zoneLabel(zone: SpacedOutZone): String = zone.label

    fun zonePoints(zone: SpacedOutZone): Int = zonePoints[zone] ?: 0

    fun reset() {
        stopTimer()
        _uiState.value = SpacedOutUiState()
        appendLog("Round reset")
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val participant = SpacedOutParticipant(handler, dog, utn)
        _uiState.update { currentState ->
            if (currentState.activeParticipant == null) {
                currentState.copy(activeParticipant = participant)
            } else {
                val updatedQueue = currentState.queue + participant
                currentState.copy(queue = updatedQueue)
            }
        }
        appendLog("Added ${participant.displayName()}")
        persistState()
    }

    fun clearParticipants() {
        _uiState.value = SpacedOutUiState()
        reset()
        appendLog("Participants cleared")
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val players = imported.map { SpacedOutParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { SpacedOutParticipant(it.handler, it.dog, it.utn) }
        applyImportedPlayers(players)
    }

    private fun applyImportedPlayers(players: List<SpacedOutParticipant>) {
        if (players.isEmpty()) return
        _uiState.value = SpacedOutUiState(
            activeParticipant = players.first(),
            queue = players.drop(1)
        )
        // Reset logs/history if needed
        appendLog("Imported ${players.size} teams")
        reset()
        persistState()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "Handler,Dog,UTN,Score,SpacedOut,ZonesCaught,Misses,OB,SweetSpot"
        val rows = buildList {
            _uiState.value.activeParticipant?.let { add(buildRoundResult(it)) }
            _uiState.value.queue.forEach { participant ->
                add(
                    SpacedOutRoundResult(
                        participant = participant,
                        score = 0,
                        spacedOutCount = 0,
                        zonesCaught = 0,
                        misses = 0,
                        ob = 0,
                        sweetSpotBonus = false
                    )
                )
            }
            addAll(_uiState.value.completed)
        }

        return buildString {
            appendLine(header)
            rows.forEach { result ->
                appendLine(
                    listOf(
                        result.participant.handler,
                        result.participant.dog,
                        result.participant.utn,
                        result.score,
                        result.spacedOutCount,
                        result.zonesCaught,
                        result.misses,
                        result.ob,
                        if (result.sweetSpotBonus) 1 else 0
                    ).joinToString(",")
                )
            }
        }
    }

    fun exportLog(): String = logEntries.value.joinToString(separator = "\n")

    fun nextParticipant() {
        val active = _uiState.value.activeParticipant ?: return
        _uiState.update { currentState ->
            val updatedCompleted = currentState.completed + buildRoundResult(active)
            val updatedQueue = currentState.queue.drop(1)
            val nextActive = updatedQueue.firstOrNull()
            currentState.copy(
                completed = updatedCompleted,
                activeParticipant = nextActive,
                queue = updatedQueue
            )
        }
        appendLog("Completed ${active.displayName()}")
        persistState()
    }

    fun skipParticipant() {
        val active = _uiState.value.activeParticipant ?: return
        _uiState.update { currentState ->
            val updatedQueue = currentState.queue + active
            val nextActive = updatedQueue.firstOrNull()
            currentState.copy(
                activeParticipant = nextActive,
                queue = updatedQueue
            )
        }
        appendLog("Skipped ${active.displayName()}")
        persistState()
    }

    fun previousParticipant() {
        val current = _uiState.value.activeParticipant ?: return
        val queue = _uiState.value.queue
        if (queue.isEmpty()) return
        val last = queue.last()
        val remaining = queue.dropLast(1)
        _uiState.update { currentState ->
            currentState.copy(
                activeParticipant = last,
                queue = listOf(current) + remaining
            )
        }
        reset()
        appendLog("Moved to previous participant")
        persistState()
    }

    fun startTimer(durationSeconds: Int = DEFAULT_TIMER_SECONDS) {
        if (_timerRunning.value) return
        _timeLeft.value = durationSeconds
        _timerRunning.value = true
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_timeLeft.value > 0 && _timerRunning.value) {
                delay(1000)
                _timeLeft.update { (it - 1).coerceAtLeast(0) }
            }
            _timerRunning.value = false
        }
        appendLog("Timer started")
    }

    fun stopTimer() {
        if (!_timerRunning.value && timerJob == null) return
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        appendLog("Timer stopped")
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = 0
        appendLog("Timer reset")
    }

    // override fun onDispose() handled in base with scope.cancel()
    private fun disposeTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun awardSpacedOutBonus() {
        _uiState.update { currentState ->
            val newScore = currentState.score + SPACED_OUT_COMPLETION_BONUS
            currentState.copy(
                score = newScore,
                spacedOutCount = currentState.spacedOutCount + 1
            )
        }
        appendLog("Spaced Out achieved")
        resetRoundState()
        persistState()
    }

    private fun resetRoundState() {
        _uiState.update { currentState ->
            currentState.copy(queue = emptyList(), activeParticipant = null)
        }
        persistState()
    }

    private fun buildRoundResult(participant: SpacedOutParticipant): SpacedOutRoundResult =
        SpacedOutRoundResult(
            participant = participant,
            score = _uiState.value.score,
            spacedOutCount = _uiState.value.spacedOutCount,
            zonesCaught = _uiState.value.zonesCaught,
            misses = _uiState.value.misses,
            ob = _uiState.value.ob,
            sweetSpotBonus = _uiState.value.sweetSpotBonus
        )

    private fun appendLog(message: String) {
        logCounter = (logCounter + 1) % 10000
        val team = _uiState.value.activeParticipant?.displayName() ?: "No team"
        val entry = "#${logCounter.toString().padStart(4, '0')} [$team] $message"
        _uiState.update { it.copy(logEntries = listOf(entry) + it.logEntries) }
    }

    private fun seedSampleParticipants() {
        val sample = listOf(
            SpacedOutParticipant("Alex Vega", "Bolt", "UTN-001"),
            SpacedOutParticipant("Jamie Reed", "Skye", "UTN-002"),
            SpacedOutParticipant("Morgan Lee", "Nova", "UTN-003")
        )
        _uiState.value = SpacedOutUiState(
            activeParticipant = sample.firstOrNull(),
            queue = sample.drop(1)
        )
    }

    private companion object {
        private const val SPACED_OUT_COMPLETION_BONUS = 25
        private const val SWEET_SPOT_BONUS_POINTS = 5
        private const val DEFAULT_TIMER_SECONDS = 60
    }
}

