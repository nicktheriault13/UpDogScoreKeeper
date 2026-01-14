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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString

@Serializable
data class TimeWarpUiState(
    val score: Int = 0,
    val misses: Int = 0,
    val ob: Int = 0,
    val clickedZones: Set<Int> = emptySet(),
    val sweetSpotClicked: Boolean = false,
    val allRollersClicked: Boolean = false,
    val fieldFlipped: Boolean = false,
    // Timer state in UI state? Or separate?
    // Usually timer is ephemeral, but if we want to persist mid-game state...
    val timeRemaining: Float = 60.0f,
    val isTimerRunning: Boolean = false,
    val activeParticipant: TimeWarpParticipant? = null,
    val queue: List<TimeWarpParticipant> = emptyList(),
    val completed: List<TimeWarpParticipant> = emptyList()
)

@Serializable
data class TimeWarpParticipantData(
    val handler: String,
    val dog: String,
    val utn: String,
    val completedAt: String
)

@Serializable
data class TimeWarpRoundExport(
    val gameMode: String,
    val exportTimestamp: String,
    val participantData: TimeWarpParticipantData,
    val roundResults: TimeWarpRoundResult,
    val roundLog: List<String>
)

@Serializable
data class PendingJsonExport(
    val filename: String,
    val content: String
)

class TimeWarpScreenModel : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    private val _uiState = MutableStateFlow(TimeWarpUiState())

    // Derived Flows
    val score = _uiState.map { it.score }.stateIn(scope, SharingStarted.Eagerly, 0)
    val misses = _uiState.map { it.misses }.stateIn(scope, SharingStarted.Eagerly, 0)
    val ob = _uiState.map { it.ob }.stateIn(scope, SharingStarted.Eagerly, 0)
    val clickedZones = _uiState.map { it.clickedZones }.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val sweetSpotClicked = _uiState.map { it.sweetSpotClicked }.stateIn(scope, SharingStarted.Eagerly, false)
    val allRollersClicked = _uiState.map { it.allRollersClicked }.stateIn(scope, SharingStarted.Eagerly, false)
    val fieldFlipped = _uiState.map { it.fieldFlipped }.stateIn(scope, SharingStarted.Eagerly, false)
    val timeRemaining = _uiState.map { it.timeRemaining }.stateIn(scope, SharingStarted.Eagerly, 60.0f)
    val isTimerRunning = _uiState.map { it.isTimerRunning }.stateIn(scope, SharingStarted.Eagerly, false)
    val activeParticipant = _uiState.map { it.activeParticipant }.stateIn(scope, SharingStarted.Eagerly, null)
    val participantQueue = _uiState.map { it.queue }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val completedParticipants = _uiState.map { it.completed }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var timerJob: Job? = null
    private var dataStore: DataStore? = null
    private val persistenceKey = "TimeWarpData.json"

    fun initPersistence(store: DataStore) {
        dataStore = store
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<TimeWarpUiState>(json)
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

    enum class ImportMode { ReplaceAll, Add }

    private fun applyImportedParticipants(players: List<TimeWarpParticipant>, mode: ImportMode) {
        if (players.isEmpty()) return
        _uiState.update { s ->
            when (mode) {
                ImportMode.ReplaceAll -> s.copy(
                    activeParticipant = players.first(),
                    queue = players.drop(1),
                    completed = emptyList()
                )
                ImportMode.Add -> {
                    if (s.activeParticipant == null) {
                        s.copy(activeParticipant = players.first(), queue = players.drop(1))
                    } else {
                        s.copy(queue = s.queue + players)
                    }
                }
            }
        }
        resetRoundStateOnly()
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseCsv(csvText).map {
            TimeWarpParticipant(handler = it.handler, dog = it.dog, utn = it.utn)
        }
        applyImportedParticipants(imported, mode)
    }

    fun importParticipantsFromXlsx(xlsxBytes: ByteArray, mode: ImportMode = ImportMode.ReplaceAll) {
        val imported = parseXlsx(xlsxBytes).map {
            TimeWarpParticipant(handler = it.handler, dog = it.dog, utn = it.utn)
        }
        applyImportedParticipants(imported, mode)
    }

    // Per-participant action log (keep current only; efficient like Fireball)
    private val _currentParticipantLog = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantLog = _currentParticipantLog.asStateFlow()

    private fun logEvent(message: String) {
        val ts = Clock.System.now().toString()
        _currentParticipantLog.update { (it + "$ts: $message").takeLast(300) }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val exportJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val _pendingJsonExport = MutableStateFlow<PendingJsonExport?>(null)
    val pendingJsonExport = _pendingJsonExport.asStateFlow()

    fun consumePendingJsonExport() {
        _pendingJsonExport.value = null
    }

    // --- Next/Skip ---

    private fun buildCurrentRoundResult(): TimeWarpRoundResult {
        val s = _uiState.value
        val zonesCaught = s.clickedZones.size

        // React behavior: time is added ON STOP; in our UI we don't have a separate Stop button yet.
        // For export, we include current score and current remaining time.
        return TimeWarpRoundResult(
            score = s.score,
            timeRemaining = s.timeRemaining,
            misses = s.misses,
            zonesCaught = zonesCaught,
            sweetSpot = s.sweetSpotClicked,
            allRollers = s.allRollersClicked
        )
    }

    fun nextParticipant() {
        logEvent("Next")
        val current = _uiState.value.activeParticipant

        if (current != null) {
            val result = buildCurrentRoundResult()
            val updated = current.copy(result = result)

            // JSON export (prompted via UI)
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val stamp = timeWarpTimestamp(now)
            val safeHandler = updated.handler.replace("\\s+".toRegex(), "")
            val safeDog = updated.dog.replace("\\s+".toRegex(), "")
            val filename = "TimeWarp_${safeHandler}_${safeDog}_$stamp.json"

            val exportData = TimeWarpRoundExport(
                gameMode = "TimeWarp",
                exportTimestamp = Clock.System.now().toString(),
                participantData = TimeWarpParticipantData(
                    handler = updated.handler,
                    dog = updated.dog,
                    utn = updated.utn,
                    completedAt = now.toString()
                ),
                roundResults = result,
                roundLog = _currentParticipantLog.value
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )

            _uiState.update { s ->
                s.copy(
                    activeParticipant = null,
                    completed = s.completed + updated
                )
            }
        }

        // Advance to next in queue; if none, create blank active participant like Fireball
        val next = _uiState.value.queue.firstOrNull()
        _currentParticipantLog.value = emptyList()

        if (next == null) {
            _uiState.update { s ->
                s.copy(activeParticipant = TimeWarpParticipant("", "", ""), queue = emptyList())
            }
        } else {
            _uiState.update { s ->
                s.copy(activeParticipant = next, queue = s.queue.drop(1))
            }
        }

        resetRoundStateOnly()
        persistState()
    }

    fun skipParticipant() {
        logEvent("Skip")
        val s = _uiState.value
        val current = s.activeParticipant ?: return
        val next = s.queue.firstOrNull() ?: return

        _uiState.update {
            it.copy(
                activeParticipant = next,
                queue = it.queue.drop(1) + current
            )
        }
        _currentParticipantLog.value = emptyList()
        resetRoundStateOnly()
        persistState()
    }

    private fun resetRoundStateOnly() {
        timerJob?.cancel()
        _uiState.update { s ->
            s.copy(
                score = 0,
                misses = 0,
                ob = 0,
                clickedZones = emptySet(),
                sweetSpotClicked = false,
                allRollersClicked = false,
                timeRemaining = 60.0f,
                isTimerRunning = false
            )
        }
    }

    fun startTimer() {
        if (!_uiState.value.isTimerRunning) {
            _uiState.update { currentState ->
                currentState.copy(
                    isTimerRunning = true,
                    timeRemaining = 60.0f // Reset to 60 seconds on start
                )
            }

            timerJob = scope.launch {
                var timeLeft = 60.0f
                while (timeLeft > 0f && _uiState.value.isTimerRunning) {
                    delay(1000L)
                    timeLeft -= 1.0f
                    _uiState.update { currentState ->
                        currentState.copy(
                            timeRemaining = timeLeft
                        )
                    }
                }
                if (timeLeft <= 0f) {
                    stopTimer()
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                isTimerRunning = false
            )
        }
        persistState()
    }

    fun setTimeManually(timeStr: String) {
        val newTime = timeStr.toFloatOrNull() ?: return
        timerJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                timeRemaining = newTime,
                isTimerRunning = false
            )
        }
        persistState()
    }

    fun incrementMisses() {
        _uiState.update { currentState ->
            currentState.copy(
                misses = currentState.misses + 1
            )
        }
        persistState()
    }

    fun incrementOb() {
        _uiState.update { currentState ->
            currentState.copy(
                ob = currentState.ob + 1
            )
        }
        persistState()
    }

    fun handleZoneClick(zone: Int) {
        if (zone !in _uiState.value.clickedZones) {
            logEvent("Zone $zone")
            _uiState.update { currentState ->
                currentState.copy(
                    score = currentState.score + 5,
                    clickedZones = currentState.clickedZones + zone
                )
            }
            persistState()
        }
    }

    fun handleSweetSpotClick() {
        logEvent("Sweet Spot")
        if (_uiState.value.clickedZones.size >= 3) {
            _uiState.update { currentState ->
                val newScore = if (!currentState.sweetSpotClicked) {
                    currentState.score + 25
                } else {
                    currentState.score - 25
                }
                currentState.copy(
                    score = newScore,
                    sweetSpotClicked = !currentState.sweetSpotClicked
                )
            }
            persistState()
        }
    }

    fun toggleAllRollers() {
        logEvent("All Rollers")
        _uiState.update { currentState ->
            currentState.copy(
                allRollersClicked = !currentState.allRollersClicked
            )
        }
        persistState()
    }

    fun flipField() {
        logEvent("Flip Field")
        _uiState.update { currentState ->
            currentState.copy(
                fieldFlipped = !currentState.fieldFlipped
            )
        }
        persistState()
    }

    fun reset() {
        logEvent("Reset")
        _uiState.value = TimeWarpUiState(activeParticipant = _uiState.value.activeParticipant, queue = _uiState.value.queue, completed = _uiState.value.completed)
        timerJob?.cancel()
        persistState()
    }
}

private fun timeWarpTimestamp(now: kotlinx.datetime.LocalDateTime): String {
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
