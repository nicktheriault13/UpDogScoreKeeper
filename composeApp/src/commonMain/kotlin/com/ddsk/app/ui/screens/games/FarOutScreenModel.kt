package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import kotlin.math.max
import kotlin.math.round

// NOTE: FarOutParticipant is defined in ExportParticipants.kt (commonMain).
// Do not redeclare or typealias it here.

@Serializable
data class FarOutLogEntry(
    val timestamp: String,
    val team: String,
    val event: String
)

data class FarOutUndoSnapshot(
    val activeIndex: Int,
    val participants: List<FarOutParticipant>,
    val throwValues: ThrowInputs,
    val missStates: MissStates,
    val sweetShotDeclined: Boolean,
    val allRollers: Boolean,
    val timer: TimerState
)

data class ThrowInputs(
    val throw1: String,
    val throw2: String,
    val throw3: String,
    val sweetShot: String
)

data class MissStates(
    val throw1Miss: Boolean,
    val throw2Miss: Boolean,
    val throw3Miss: Boolean,
    val sweetShotMiss: Boolean
)

data class TimerState(
    val isRunning: Boolean,
    val isPaused: Boolean,
    val positionMillis: Long,
    val durationMillis: Long
)

data class TimerDisplay(
    val secondsRemaining: Int,
    val isRunning: Boolean,
    val isPaused: Boolean
)

data class TieWarning(
    val visible: Boolean = false,
    val message: String = ""
)

data class FarOutState(
    val participants: List<FarOutParticipant> = emptyList(),
    val activeIndex: Int = 0,
    val throwInputs: ThrowInputs = ThrowInputs("", "", "", ""),
    val missStates: MissStates = MissStates(false, false, false, false),
    val sweetShotDeclined: Boolean = true, // Default to declined
    val allRollersPressed: Boolean = false,
    val sweetShotEditable: Boolean = true,
    val score: Double = 0.0,
    val logEntries: List<FarOutLogEntry> = emptyList(),
    val timerDisplay: TimerDisplay = TimerDisplay(90, false, false),
    val helpText: String = FAR_OUT_HELP_TEXT,
    val loadingHelp: Boolean = false,
    val showHelp: Boolean = false,
    val showAddParticipant: Boolean = false,
    val showClearPrompt: Boolean = false,
    val tieWarning: TieWarning = TieWarning(),
    val showTeamModal: Boolean = false,
    val participantCountWithoutScores: Int = 0,
    val participantQueueSnapshot: List<FarOutParticipant> = emptyList(),
    val undoAvailable: Boolean = false
)

private const val DEFAULT_TIMER_MILLIS = 90_000L
private const val MAX_UNDO_LEVELS = 250
private val json = Json { prettyPrint = true }

// Serializable data classes for JSON export
@Serializable
data class FarOutJsonExportData(
    val gameMode: String,
    val exportTimestamp: String,
    val participant: FarOutParticipantDataJson,
    val roundResults: FarOutRoundResultsJson
)

@Serializable
data class FarOutParticipantDataJson(
    val Handler: String,
    val Dog: String,
    val UTN: String
)

@Serializable
data class FarOutRoundResultsJson(
    val throw1: String,
    val throw2: String,
    val throw3: String,
    val sweetShot: String,
    val allRollers: Boolean,
    val score: Double,
    val misses: Int,
    val sweetShotDeclined: Boolean
)

class FarOutScreenModel(
    private val logger: FarOutLogger = ConsoleFarOutLogger()
) : ScreenModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDispose() {
        scope.cancel()
        super.onDispose()
    }

    private val _state = MutableStateFlow(FarOutState())
    val state: StateFlow<FarOutState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<FarOutUndoSnapshot>()

    // JSON export state (for Android sharing)
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

    private var timerJob: Job? = null
    private var timerSoundJob: Job? = null

    private var dataStore: com.ddsk.app.persistence.DataStore? = null
    private val persistenceKey = "FarOutData.json"

    fun initPersistence(store: com.ddsk.app.persistence.DataStore) {
        dataStore = store
        // Desktop builds don't provide Dispatchers.Main by default.
        // Keep persistence IO/deserialization off Main.
        scope.launch {
            val json = store.load(persistenceKey)
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<PersistedFarOutData>(json)
                    _state.update { current ->
                        current.copy(
                            participants = saved.participants,
                            activeIndex = saved.activeIndex,
                            participantCountWithoutScores = saved.participants.count { !hasScoringData(it) }
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateThrow(label: String, rawInput: String) {
        val sanitized = sanitizeThrowInput(rawInput)
        val rounded = sanitized.toDoubleOrNull()?.let { round(it * 2) / 2 }?.toString() ?: sanitized
        pushUndo()
        _state.update { current ->
            val updatedInputs = when (label) {
                "Throw 1" -> current.throwInputs.copy(throw1 = rounded)
                "Throw 2" -> current.throwInputs.copy(throw2 = rounded)
                "Throw 3" -> current.throwInputs.copy(throw3 = rounded)
                else -> current.throwInputs.copy(sweetShot = rounded)
            }

            // If entering a value for Sweet Shot, automatically un-decline it
            val updatedDeclined = if (label == "Sweet Shot" && rounded.isNotBlank()) false else current.sweetShotDeclined

            val updatedScore = calculateScore(updatedInputs, current.missStates, updatedDeclined)
            current.copy(throwInputs = updatedInputs, sweetShotDeclined = updatedDeclined, score = updatedScore)
        }
        logEvent("Input $label set to $rounded")
    }

    fun toggleMiss(label: String) {
        pushUndo()
        _state.update { current ->
            val updatedMiss = when (label) {
                "Throw 1" -> current.missStates.copy(throw1Miss = !current.missStates.throw1Miss)
                "Throw 2" -> current.missStates.copy(throw2Miss = !current.missStates.throw2Miss)
                "Throw 3" -> current.missStates.copy(throw3Miss = !current.missStates.throw3Miss)
                else -> current.missStates.copy(sweetShotMiss = !current.missStates.sweetShotMiss)
            }

            // If clicking miss for Sweet Shot, automatically un-decline it
            val updatedDeclined = if (label == "Sweet Shot") false else current.sweetShotDeclined

            val updatedScore = calculateScore(current.throwInputs, updatedMiss, updatedDeclined)
            current.copy(missStates = updatedMiss, sweetShotDeclined = updatedDeclined, score = updatedScore)
        }
        logEvent("Miss toggled on $label")
    }

    fun toggleDeclined() {
        pushUndo()
        _state.update { current ->
            val newDeclined = !current.sweetShotDeclined
            val sanitizedMiss = if (newDeclined) current.missStates.copy(sweetShotMiss = false) else current.missStates
            current.copy(
                sweetShotDeclined = newDeclined,
                missStates = sanitizedMiss,
                throwInputs = current.throwInputs.copy(sweetShot = if (newDeclined) "" else current.throwInputs.sweetShot),
                score = calculateScore(current.throwInputs.copy(sweetShot = if (newDeclined) "" else current.throwInputs.sweetShot), sanitizedMiss, newDeclined)
            )
        }
        logEvent("Sweet Shot declined toggled")
    }

    fun toggleAllRollers() {
        pushUndo()
        _state.update { current -> current.copy(allRollersPressed = !current.allRollersPressed) }
        logEvent("All Rollers toggled")
    }

    fun resetRound() {
        pushUndo()
        _state.update { current ->
            current.copy(
                throwInputs = ThrowInputs("", "", "", ""),
                missStates = MissStates(false, false, false, false),
                sweetShotDeclined = true, // Default to declined
                allRollersPressed = false,
                score = 0.0
            )
        }
        logEvent("Round reset")
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val participant = FarOutParticipant(handler, dog, utn)
        pushUndo()
        _state.update { current ->
            // FourWayPlay-style queue semantics: index 0 is always the active team.
            val updated = current.participants + participant
            current.copy(
                participants = updated,
                activeIndex = 0,
                participantCountWithoutScores = updated.count { !hasScoringData(it) }
            )
        }
        logEvent("Added $handler & $dog")
        persistParticipants()
    }

    fun clearParticipants() {
        pushUndo()
        _state.value = FarOutState()
        undoStack.clear()
        logEvent("Participants cleared")
        // screenModelScope.launch { storage.clearParticipants() }
    }

    fun nextParticipant(autoExport: Boolean = false) {
        val active = _state.value.participants.getOrNull(_state.value.activeIndex) ?: return
        val updatedActive = active.copy(
            throw1 = _state.value.throwInputs.throw1,
            throw2 = _state.value.throwInputs.throw2,
            throw3 = _state.value.throwInputs.throw3,
            sweetShot = _state.value.throwInputs.sweetShot,
            sweetShotDeclined = _state.value.sweetShotDeclined,
            allRollers = _state.value.allRollersPressed,
            throw1Miss = _state.value.missStates.throw1Miss,
            throw2Miss = _state.value.missStates.throw2Miss,
            throw3Miss = _state.value.missStates.throw3Miss,
            sweetShotMiss = _state.value.missStates.sweetShotMiss,
            score = _state.value.score,
            misses = listOf(
                _state.value.missStates.throw1Miss,
                _state.value.missStates.throw2Miss,
                _state.value.missStates.throw3Miss,
                _state.value.missStates.sweetShotMiss
            ).count { it }
        )

        // FourWayPlay-style queue rotation: move active to end, next becomes index 0.
        val filtered = _state.value.participants.filterIndexed { index, _ -> index != _state.value.activeIndex }
        val rotated = filtered + updatedActive

        pushUndo()
        _state.update { current ->
            current.copy(
                participants = rotated,
                activeIndex = 0,
                throwInputs = ThrowInputs("", "", "", ""),
                missStates = MissStates(false, false, false, false),
                sweetShotDeclined = true, // Default to declined
                allRollersPressed = false,
                score = 0.0,
                participantCountWithoutScores = rotated.count { !hasScoringData(it) }
            )
        }
        logEvent("Completed ${active.handler} & ${active.dog}")
        persistParticipants()

        // Auto-export JSON for this participant (emit via state flow for Android sharing)
        if (autoExport) {
            exportParticipantJson(updatedActive)
        }
    }

    fun skipParticipant() {
        val participants = _state.value.participants
        if (participants.isEmpty()) return
        pushUndo()
        _state.update { current ->
            // FourWayPlay-style skip: move active to end, next becomes active.
            val active = current.participants.getOrNull(current.activeIndex)
            val remaining = current.participants.filterIndexed { idx, _ -> idx != current.activeIndex }
            val rotated = if (active != null) remaining + active else remaining
            current.copy(participants = rotated, activeIndex = 0)
        }
        logEvent("Participant skipped")
    }

    fun previousParticipant() {
        val participants = _state.value.participants
        if (participants.isEmpty()) return
        pushUndo()
        _state.update { current ->
            // FourWayPlay-style previous: take last and move it to front.
            if (current.participants.size <= 1) return@update current.copy(activeIndex = 0)
            val last = current.participants.last()
            val rest = current.participants.dropLast(1)
            current.copy(participants = listOf(last) + rest, activeIndex = 0)
        }
        logEvent("Moved to previous participant")
    }

    enum class ImportMode {
        Add,
        ReplaceAll
    }

    fun importParticipantsFromCsv(csvText: String, mode: ImportMode = ImportMode.ReplaceAll) {
        // Keep using the shared CSV parser (same as FourWayPlay), but do it synchronously
        // and without custom per-screen CSV parsing helpers.
        try {
            val imported = parseCsv(csvText)
            val participants = imported.map {
                FarOutParticipant(
                    handler = it.handler,
                    dog = it.dog,
                    utn = it.utn,
                    jumpHeight = it.jumpHeight,
                    heightDivision = it.heightDivision,
                    clubDivision = it.clubDivision
                )
            }
            applyImportedParticipants(participants, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            logEvent("Import CSV failed: ${e.message}")
        }
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray, mode: ImportMode = ImportMode.ReplaceAll) {
        // Match FourWayPlay: parseXlsx + map + set state.
        try {
            val imported = parseXlsx(xlsxData)
            val participants = imported.map {
                FarOutParticipant(
                    handler = it.handler,
                    dog = it.dog,
                    utn = it.utn,
                    jumpHeight = it.jumpHeight,
                    heightDivision = it.heightDivision,
                    clubDivision = it.clubDivision
                )
            }
            applyImportedParticipants(participants, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            logEvent("Import XLSX failed: ${e.message}")
        }
    }

    private fun applyImportedParticipants(newParticipants: List<FarOutParticipant>, mode: ImportMode) {
        if (newParticipants.isEmpty()) {
            logEvent("Import: 0 participants (check sheet + headers)")
            persistParticipants()
            return
        }

        pushUndo()
        _state.update { s ->
            val merged = when (mode) {
                ImportMode.ReplaceAll -> newParticipants
                ImportMode.Add -> s.participants + newParticipants
            }
            // FourWayPlay-style queue: active is always index 0.
            s.copy(
                participants = merged,
                activeIndex = 0,
                throwInputs = ThrowInputs("", "", "", ""),
                missStates = MissStates(false, false, false, false),
                sweetShotDeclined = false,
                allRollersPressed = false,
                score = 0.0,
                participantCountWithoutScores = merged.count { !hasScoringData(it) }
            )
        }

        logEvent(
            when (mode) {
                ImportMode.ReplaceAll -> "Imported ${newParticipants.size} from ${if (newParticipants.size == 1) "team" else "teams"} (replace)"
                ImportMode.Add -> "Imported ${newParticipants.size} from ${if (newParticipants.size == 1) "team" else "teams"} (add)"
            }
        )
        persistParticipants()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "Handler,Dog,UTN,Jump Height,Height Division,Club Division,Throw1,Throw2,Throw3,SweetShot,SweetDeclined,AllRollers,Score,Misses"
        return buildString {
            appendLine(header)
            _state.value.participants.forEach { participant ->
                appendLine(
                    listOf(
                        participant.handler,
                        participant.dog,
                        participant.utn,
                        participant.jumpHeight,
                        participant.heightDivision,
                        participant.clubDivision,
                        participant.throw1,
                        participant.throw2,
                        participant.throw3,
                        participant.sweetShot,
                        participant.sweetShotDeclined,
                        participant.allRollers,
                        participant.score,
                        participant.misses
                    ).joinToString(",")
                )
            }
        }
    }

    suspend fun exportParticipantsAsXlsx(templateBytes: ByteArray? = null): ExportResult? {
        val participants = _state.value.participants
        if (participants.isEmpty()) return null

        // Use template bytes if provided, otherwise check if we can get it from assets or create basic
        // For now, if templateBytes is null, we can try to use a basic structure or fail gracefully
        // Ideally we should have the template loaded.

        val bytes = if (templateBytes != null && templateBytes.isNotEmpty()) {
            generateFarOutXlsx(participants, templateBytes)
        } else {
            // Fallback or error? Let's return null to signal failure for now as we really need the template
            // Or we could implement a no-template generator if needed
            return null
        }

        val timestamp = Clock.System.now().toString().replace(":", "-").substringBefore(".")
        return ExportResult("FarOut_Scores_$timestamp.xlsm", bytes)
    }

    fun exportLog(): String = _state.value.logEntries.joinToString("\n") { "${it.timestamp} - ${it.team}: ${it.event}" }

    fun undo() {
        val snapshot = undoStack.removeFirstOrNull() ?: return
        _state.value = _state.value.copy(
            participants = snapshot.participants,
            activeIndex = snapshot.activeIndex,
            throwInputs = snapshot.throwValues,
            missStates = snapshot.missStates,
            sweetShotDeclined = snapshot.sweetShotDeclined,
            allRollersPressed = snapshot.allRollers,
            score = calculateScore(snapshot.throwValues, snapshot.missStates, snapshot.sweetShotDeclined),
            timerDisplay = snapshot.timer.toDisplay(),
            undoAvailable = undoStack.isNotEmpty()
        )
    }

    fun toggleHelp(visible: Boolean) {
        _state.update { it.copy(showHelp = visible) }
    }

    fun showAddParticipant(show: Boolean) {
        _state.update { it.copy(showAddParticipant = show) }
    }

    fun setHelpText(text: String) {
        _state.update { it.copy(helpText = text, loadingHelp = false) }
    }

    fun showHelpLoading() {
        _state.update { it.copy(loadingHelp = true) }
    }

    fun showClearPrompt(show: Boolean) {
        _state.update { it.copy(showClearPrompt = show) }
    }

    fun showTeamModal(show: Boolean) {
        _state.update { it.copy(showTeamModal = show) }
    }

    fun resolveTieWarning(message: String?) {
        _state.update { current -> current.copy(tieWarning = TieWarning(visible = message != null, message = message ?: "")) }
    }

    fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            _state.update { it.copy(timerDisplay = it.timerDisplay.copy(isRunning = true, isPaused = false)) }
            var millisLeft = DEFAULT_TIMER_MILLIS
            while (millisLeft > 0 && _state.value.timerDisplay.isRunning) {
                delay(1000)
                millisLeft -= 1000
                val seconds = max(0, (millisLeft / 1000).toInt())
                _state.update { it.copy(timerDisplay = it.timerDisplay.copy(secondsRemaining = seconds)) }
            }
            stopTimer()
        }
        logEvent("Timer started")
    }

    fun pauseOrResumeTimer() {
        _state.update { current ->
            val nowPaused = !current.timerDisplay.isPaused
            current.copy(timerDisplay = current.timerDisplay.copy(isPaused = nowPaused))
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { current ->
            current.copy(timerDisplay = TimerDisplay(secondsRemaining = 90, isRunning = false, isPaused = false))
        }
        logEvent("Timer stopped")
    }

    fun saveHelpText(text: String) {
        // storage.saveHelpText(text)
    }

    private fun persistParticipants() {
        val store = dataStore ?: return
        val data = PersistedFarOutData(_state.value.participants, _state.value.activeIndex)
        scope.launch {
            try {
                val json = Json.encodeToString(data)
                store.save(persistenceKey, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadPersistedData() {
        // Redundant with initPersistence, can be removed or kept as empty stub if interface requires it
        // val saved = storage.loadParticipants()
        // val log = storage.loadLog()
        // _state.update { current ->
        //     current.copy(
        //         participants = saved.participants,
        //         activeIndex = saved.activeIndex,
        //         logEntries = log,
        //         participantCountWithoutScores = saved.participants.count { !hasScoringData(it) }
        //     )
        // }
    }

    private fun sanitizeThrowInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        return if (filtered.count { it == '.' } > 1) filtered.substringBeforeLast('.') else filtered
    }

    private fun calculateScore(throws: ThrowInputs, miss: MissStates, declined: Boolean): Double {
        val throwValues = listOf(throws.throw1, throws.throw2, throws.throw3)
            .mapIndexed { index, value ->
                val missFlag = when (index) {
                    0 -> miss.throw1Miss
                    1 -> miss.throw2Miss
                    else -> miss.throw3Miss
                }
                if (missFlag) 0.0 else value.toDoubleOrNull() ?: 0.0
            }
        val sweetShotValue = if (miss.sweetShotMiss) 0.0 else throws.sweetShot.toDoubleOrNull() ?: 0.0
        val sorted = throwValues.sortedDescending()

        // If sweet shot is NOT declined, score = highest 2 throws + sweet shot (0 if miss)
        // If sweet shot IS declined, score = sum of all 3 throws
        return if (!declined) {
            sweetShotValue + sorted.take(2).sum()
        } else {
            throwValues.sum()
        }
    }

    private fun logEvent(message: String) {
        val participant = _state.value.participants.getOrNull(_state.value.activeIndex)
        val team = participant?.let { "${it.handler} & ${it.dog}" } ?: "No team"
        val entry = FarOutLogEntry(timestamp = Clock.System.now().toString(), team = team, event = message)
        _state.update { it.copy(logEntries = listOf(entry) + it.logEntries) }
        scope.launch { /* storage.saveLog(_state.value.logEntries) */ }
        logger.log(event = message, team = team)
    }

    private fun hasScoringData(participant: FarOutParticipant): Boolean {
        return participant.throw1.isNotBlank() || participant.throw2.isNotBlank() || participant.throw3.isNotBlank() || participant.sweetShot.isNotBlank() || participant.score > 0
    }

    private fun pushUndo() {
        if (undoStack.size >= MAX_UNDO_LEVELS) undoStack.removeLast()
        undoStack.addFirst(
            FarOutUndoSnapshot(
                activeIndex = _state.value.activeIndex,
                participants = _state.value.participants,
                throwValues = _state.value.throwInputs,
                missStates = _state.value.missStates,
                sweetShotDeclined = _state.value.sweetShotDeclined,
                allRollers = _state.value.allRollersPressed,
                timer = TimerState(
                    isRunning = _state.value.timerDisplay.isRunning,
                    isPaused = _state.value.timerDisplay.isPaused,
                    positionMillis = _state.value.timerDisplay.secondsRemaining * 1000L,
                    durationMillis = DEFAULT_TIMER_MILLIS
                )
            )
        )
        _state.update { it.copy(undoAvailable = true) }
    }

    private fun TimerState.toDisplay(): TimerDisplay = TimerDisplay(
        secondsRemaining = (positionMillis / 1000).toInt(),
        isRunning = isRunning,
        isPaused = isPaused
    )

    private fun exportParticipantJson(participant: FarOutParticipant) {
        try {
            val now = Clock.System.now()
            val timestamp = now.toString().replace(":", "-").substringBefore(".")
            val handlerPart = participant.handler.replace(" ", "_").ifBlank { "Unknown" }
            val dogPart = participant.dog.replace(" ", "_").ifBlank { "Unknown" }
            val fileName = "FarOut_${handlerPart}_${dogPart}_${timestamp}.json"

            val exportData = FarOutJsonExportData(
                gameMode = "FarOut",
                exportTimestamp = now.toString(),
                participant = FarOutParticipantDataJson(
                    Handler = participant.handler,
                    Dog = participant.dog,
                    UTN = participant.utn
                ),
                roundResults = FarOutRoundResultsJson(
                    throw1 = participant.throw1,
                    throw2 = participant.throw2,
                    throw3 = participant.throw3,
                    sweetShot = participant.sweetShot,
                    allRollers = participant.allRollers,
                    score = participant.score,
                    misses = participant.misses,
                    sweetShotDeclined = participant.sweetShotDeclined
                )
            )

            val jsonString = json.encodeToString(exportData)

            // Emit via state flow for cross-platform handling (share on Android, save on Desktop)
            _pendingJsonExport.value = PendingJsonExport(fileName, jsonString)

            logEvent("JSON Export: $fileName created for ${participant.handler} & ${participant.dog}")
        } catch (e: Exception) {
            e.printStackTrace()
            logEvent("JSON Export Error: Failed to export data for ${participant.handler} & ${participant.dog}")
        }
    }

    fun dispose() {
        timerJob?.cancel()
        timerSoundJob?.cancel()
    }
}

interface FarOutStorage {
    suspend fun saveParticipants(participants: List<FarOutParticipant>, activeIndex: Int)
    suspend fun loadParticipants(): PersistedFarOutData
    suspend fun clearParticipants()
    suspend fun saveLog(entries: List<FarOutLogEntry>)
    suspend fun loadLog(): List<FarOutLogEntry>
    suspend fun shareParticipantJson(handler: String, dog: String, payload: String)
    fun saveHelpText(text: String)
}

@Serializable
data class PersistedFarOutData(
    val participants: List<FarOutParticipant> = emptyList(),
    val activeIndex: Int = 0
)

class InMemoryFarOutStorage : FarOutStorage {
    private var persistedParticipants: PersistedFarOutData = PersistedFarOutData()
    private var logEntries: List<FarOutLogEntry> = emptyList()
    private var helpText: String = FAR_OUT_HELP_TEXT

    override suspend fun saveParticipants(participants: List<FarOutParticipant>, activeIndex: Int) {
        persistedParticipants = PersistedFarOutData(participants, activeIndex)
    }

    override suspend fun loadParticipants(): PersistedFarOutData = persistedParticipants

    override suspend fun clearParticipants() {
        persistedParticipants = PersistedFarOutData()
    }

    override suspend fun saveLog(entries: List<FarOutLogEntry>) {
        logEntries = entries
    }

    override suspend fun loadLog(): List<FarOutLogEntry> = logEntries

    override suspend fun shareParticipantJson(handler: String, dog: String, payload: String) {
        println("Sharing Far Out participant: $handler & $dog, bytes=${payload.length}")
    }

    override fun saveHelpText(text: String) {
        helpText = text
    }
}

interface FarOutLogger {
    fun log(event: String, team: String)
}

class ConsoleFarOutLogger : FarOutLogger {
    override fun log(event: String, team: String) {
        println("[$team] $event")
    }
}

const val FAR_OUT_HELP_TEXT = "Far Out — Button functions\n\nThis file documents every interactive button shown on the Far Out screen."
