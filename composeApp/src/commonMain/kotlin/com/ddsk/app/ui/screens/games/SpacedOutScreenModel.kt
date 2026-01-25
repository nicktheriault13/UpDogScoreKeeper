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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    val utn: String,
    val heightDivision: String = ""
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
    val sweetSpotBonus: Boolean,
    val allRollers: Boolean
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
    val allRollers: Boolean = false,
    val fieldFlipped: Boolean = false,
    val clickedZones: Set<SpacedOutZone> = emptySet(),
    val logEntries: List<String> = emptyList(),
    val currentParticipantLog: List<String> = emptyList(),
    // React parity: prevent clicking the same zone twice in a row.
    val lastZoneClicked: SpacedOutZone? = null
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
    val allRollersOn: StateFlow<Boolean> = _uiState.map { it.allRollers }.stateIn(scope, SharingStarted.Eagerly, false)

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

    // React parity: only 4 scoring zones exist on the field UI.
    // (We reuse the existing enum values for persistence compatibility.)
    private val scoringZones: Set<SpacedOutZone> = setOf(
        SpacedOutZone.Zone1, // zone1
        SpacedOutZone.Zone2, // Sweet Spot (zone button)
        SpacedOutZone.Zone3, // zone2
        SpacedOutZone.Zone4  // zone3
    )

    fun handleZoneClick(zone: SpacedOutZone) {
         // Ignore non-scoring zones (defensive; UI should only send these 4)
         if (zone !in scoringZones) return

         // React parity: prevent clicking the same zone twice in a row
         if (_uiState.value.lastZoneClicked == zone) return

         _uiState.update { currentState ->
             val newZonesCaught = currentState.zonesCaught + 1
             val newClickedZones = currentState.clickedZones + zone

             // React parity: each zone is worth +5
             var newScore = currentState.score + 5

             val allZonesClicked = scoringZones.all { it in newClickedZones }
             val newSpacedOutCount = if (allZonesClicked) currentState.spacedOutCount + 1 else currentState.spacedOutCount

             if (allZonesClicked) {
                 // React parity: Spaced Out awards +25 and clears the board
                 newScore += 25
             }

             currentState.copy(
                 score = newScore,
                 zonesCaught = newZonesCaught,
                 spacedOutCount = newSpacedOutCount,
                 clickedZones = if (allZonesClicked) emptySet() else newClickedZones,
                 lastZoneClicked = if (allZonesClicked) null else zone
             )
         }

         // Logging + persistence
         if (_uiState.value.clickedZones.isEmpty() && _uiState.value.spacedOutCount > 0) {
             appendLog("Spaced Out achieved")
         } else {
             // Zone2 is displayed as "Sweet Spot" in the UI
             val displayLabel = if (zone == SpacedOutZone.Zone2) "Sweet Spot" else "zone${zone.label}"
             appendLog("$displayLabel clicked (+5 pts)")
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
        val isEnabled = _uiState.value.sweetSpotBonus
        appendLog(if (isEnabled) "Sweet Spot Bonus enabled (+$SWEET_SPOT_BONUS_POINTS pts)" else "Sweet Spot Bonus disabled (-$SWEET_SPOT_BONUS_POINTS pts)")
        persistState()
    }

    fun toggleAllRollers() {
        _uiState.update { it.copy(allRollers = !it.allRollers) }
        appendLog("All Rollers toggled")
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
        _uiState.update { s ->
            s.copy(
                score = 0,
                spacedOutCount = 0,
                zonesCaught = 0,
                misses = 0,
                ob = 0,
                sweetSpotBonus = false,
                allRollers = false,
                fieldFlipped = false,
                clickedZones = emptySet(),
                currentParticipantLog = emptyList(),
                lastZoneClicked = null
            )
        }
        appendLog("Round reset")
        persistState()
    }

    fun clearParticipants() {
        stopTimer()
        _uiState.value = SpacedOutUiState()
        appendLog("Participants cleared")
        persistState()
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val participant = SpacedOutParticipant(handler = handler, dog = dog, utn = utn, heightDivision = "")
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

    private fun logEvent(message: String) {
        val ts = Clock.System.now().toString()
        _uiState.update { s ->
            s.copy(currentParticipantLog = (s.currentParticipantLog + "$ts: $message").takeLast(300))
        }
    }

    private fun appendLog(message: String) {
        logCounter = (logCounter + 1) % 10000
        val team = _uiState.value.activeParticipant?.displayName() ?: "No team"
        val entry = "#${logCounter.toString().padStart(4, '0')} [$team] $message"
        _uiState.update {
            it.copy(
                logEntries = listOf(entry) + it.logEntries,
                currentParticipantLog = (it.currentParticipantLog + entry).takeLast(300)
            )
        }
    }

    private fun buildRoundResult(participant: SpacedOutParticipant): SpacedOutRoundResult =
        SpacedOutRoundResult(
            participant = participant,
            score = _uiState.value.score,
            spacedOutCount = _uiState.value.spacedOutCount,
            zonesCaught = _uiState.value.zonesCaught,
            misses = _uiState.value.misses,
            ob = _uiState.value.ob,
            sweetSpotBonus = _uiState.value.sweetSpotBonus,
            allRollers = _uiState.value.allRollers
        )

    fun startTimer(durationSeconds: Int = DEFAULT_TIMER_SECONDS) {
        if (_timerRunning.value) return
        _timeLeft.value = durationSeconds
        _timerRunning.value = true
        appendLog("Timer started")
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_timeLeft.value > 0 && _timerRunning.value) {
                delay(1000)
                _timeLeft.update { (it - 1).coerceAtLeast(0) }
            }
            _timerRunning.value = false
        }
        persistState()
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        appendLog("Timer stopped")
        persistState()
    }

    fun resetTimer() {
        stopTimer()
        _timeLeft.value = 0
        persistState()
    }

    fun skipParticipant() {
        val active = _uiState.value.activeParticipant ?: return
        // Rotate active to end of queue (no save)
        _uiState.update { s ->
            val rotated = s.queue + active
            val nextActive = rotated.firstOrNull()
            val nextQueue = if (nextActive != null) rotated.drop(1) else emptyList()

            s.copy(
                activeParticipant = nextActive,
                queue = nextQueue,
                score = 0,
                spacedOutCount = 0,
                zonesCaught = 0,
                misses = 0,
                ob = 0,
                sweetSpotBonus = false,
                allRollers = false,
                fieldFlipped = false,
                clickedZones = emptySet(),
                currentParticipantLog = emptyList(),
                lastZoneClicked = null
            )
        }
        appendLog("Skipped participant")
        persistState()
    }

    fun previousParticipant() {
        val current = _uiState.value.activeParticipant ?: return
        val queue = _uiState.value.queue
        if (queue.isEmpty()) return

        val last = queue.last()
        val remaining = queue.dropLast(1)

        _uiState.update { s ->
            s.copy(
                activeParticipant = last,
                queue = listOf(current) + remaining,
                score = 0,
                spacedOutCount = 0,
                zonesCaught = 0,
                misses = 0,
                ob = 0,
                sweetSpotBonus = false,
                allRollers = false,
                fieldFlipped = false,
                clickedZones = emptySet(),
                currentParticipantLog = emptyList(),
                lastZoneClicked = null
            )
        }
        appendLog("Previous participant")
        persistState()
    }

    private fun applyImportedPlayers(players: List<SpacedOutParticipant>) {
        if (players.isEmpty()) return
        _uiState.update { s ->
            s.copy(
                activeParticipant = players.first(),
                queue = players.drop(1),
                completed = emptyList(),
                score = 0,
                spacedOutCount = 0,
                zonesCaught = 0,
                misses = 0,
                ob = 0,
                sweetSpotBonus = false,
                allRollers = false,
                fieldFlipped = false,
                clickedZones = emptySet(),
                currentParticipantLog = emptyList(),
                lastZoneClicked = null
            )
        }
        appendLog("Imported ${players.size} teams")
        persistState()
    }

    fun importParticipantsFromCsv(csvText: String) {
        val imported = parseCsv(csvText)
        val players = imported.map { SpacedOutParticipant(it.handler, it.dog, it.utn, it.heightDivision) }
        if (players.isEmpty()) {
            appendLog("Import CSV: 0 participants (check file format)")
            persistState()
            return
        }
        applyImportedPlayers(players)
        appendLog("Imported ${players.size} participants from CSV")
        persistState()
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val imported = parseXlsx(xlsxData)
        val players = imported.map { SpacedOutParticipant(it.handler, it.dog, it.utn, it.heightDivision) }
        if (players.isEmpty()) {
            appendLog("Import XLSX: 0 participants (check sheet + headers)")
            persistState()
            return
        }
        applyImportedPlayers(players)
        appendLog("Imported ${players.size} participants from XLSX")
        persistState()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "Handler,Dog,UTN,Score,SpacedOut,ZonesCaught,Misses,OB,SweetSpot,AllRollers"
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
                        sweetSpotBonus = false,
                        allRollers = false
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
                        if (result.sweetSpotBonus) 1 else 0,
                        if (result.allRollers) 1 else 0
                    ).joinToString(",")
                )
            }
        }
    }

    fun exportLog(): String = logEntries.value.joinToString(separator = "\n")

    private val exportJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

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

    fun nextParticipant() {
        val active = _uiState.value.activeParticipant ?: return
        val result = buildRoundResult(active)

        runCatching {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            fun pad2(n: Int) = n.toString().padStart(2, '0')
            val stamp = buildString {
                append(now.year)
                append(pad2(now.monthNumber))
                append(pad2(now.dayOfMonth))
                append('_')
                append(pad2(now.hour))
                append(pad2(now.minute))
                append(pad2(now.second))
            }
            val safeHandler = active.handler.trim().replace("\\s+".toRegex(), "_")
            val safeDog = active.dog.trim().replace("\\s+".toRegex(), "_")
            val filename = "SpacedOut_${safeHandler}_${safeDog}_$stamp.json"

            val exportData = SpacedOutRoundExport(
                gameMode = "SpacedOut",
                exportTimestamp = Clock.System.now().toString(),
                participant = SpacedOutParticipantExport(
                    handler = active.handler,
                    dog = active.dog,
                    utn = active.utn,
                    heightDivision = active.heightDivision
                ),
                roundResults = SpacedOutRoundResultsExport(
                    zonesCaught = result.zonesCaught,
                    score = result.score,
                    spacedOut = result.spacedOutCount,
                    misses = result.misses,
                    ob = result.ob,
                    sweetSpotAchieved = result.sweetSpotBonus,
                    allRollersAchieved = result.allRollers
                ),
                roundLog = _uiState.value.currentParticipantLog
            )

            _pendingJsonExport.value = PendingJsonExport(
                filename = filename,
                content = exportJson.encodeToString(exportData)
            )
        }

        _uiState.update { s ->
            val remainingQueue = s.queue
            val nextActive = remainingQueue.firstOrNull()
            val nextQueue = if (nextActive != null) remainingQueue.drop(1) else emptyList()

            s.copy(
                completed = s.completed + result,
                activeParticipant = nextActive,
                queue = nextQueue,
                score = 0,
                spacedOutCount = 0,
                zonesCaught = 0,
                misses = 0,
                ob = 0,
                sweetSpotBonus = false,
                allRollers = false,
                fieldFlipped = false,
                clickedZones = emptySet(),
                currentParticipantLog = emptyList(),
                lastZoneClicked = null
            )
        }

        appendLog("Next participant")
        persistState()
    }

    @Serializable
    private data class SpacedOutParticipantExport(
        val handler: String,
        val dog: String,
        val utn: String,
        val heightDivision: String = ""
    )

    @Serializable
    private data class SpacedOutRoundResultsExport(
        val zonesCaught: Int,
        val score: Int,
        val spacedOut: Int,
        val misses: Int,
        val ob: Int,
        val sweetSpotAchieved: Boolean,
        val allRollersAchieved: Boolean
    )

    @Serializable
    private data class SpacedOutRoundExport(
        val gameMode: String,
        val exportTimestamp: String,
        val participant: SpacedOutParticipantExport,
        val roundResults: SpacedOutRoundResultsExport,
        val roundLog: List<String> = emptyList()
    )

    fun exportParticipantsAsXlsx(templateBytes: ByteArray): ByteArray {
        val exportRows = buildList {
            addAll(_uiState.value.completed.map {
                SpacedOutExportParticipant(
                    handler = it.participant.handler,
                    dog = it.participant.dog,
                    utn = it.participant.utn,
                    zonesCaught = it.zonesCaught,
                    spacedOut = it.spacedOutCount,
                    misses = it.misses,
                    ob = it.ob,
                    sweetSpot = if (it.sweetSpotBonus) 1 else 0,
                    allRollers = if (it.allRollers) 1 else 0,
                    heightDivision = it.participant.heightDivision
                )
            })

            _uiState.value.activeParticipant?.let { active ->
                val r = buildRoundResult(active)
                add(
                    SpacedOutExportParticipant(
                        handler = active.handler,
                        dog = active.dog,
                        utn = active.utn,
                        zonesCaught = r.zonesCaught,
                        spacedOut = r.spacedOutCount,
                        misses = r.misses,
                        ob = r.ob,
                        sweetSpot = if (r.sweetSpotBonus) 1 else 0,
                        allRollers = if (r.allRollers) 1 else 0,
                        heightDivision = active.heightDivision
                    )
                )
            }

            _uiState.value.queue.forEach { p ->
                add(
                    SpacedOutExportParticipant(
                        handler = p.handler,
                        dog = p.dog,
                        utn = p.utn,
                        zonesCaught = 0,
                        spacedOut = 0,
                        misses = 0,
                        ob = 0,
                        sweetSpot = 0,
                        allRollers = 0,
                        heightDivision = p.heightDivision
                    )
                )
            }
        }

        return generateSpacedOutXlsx(exportRows, templateBytes)
    }

    private companion object {
        private const val SPACED_OUT_COMPLETION_BONUS = 25
        private const val SWEET_SPOT_BONUS_POINTS = 5
        private const val DEFAULT_TIMER_SECONDS = 60
    }
}
