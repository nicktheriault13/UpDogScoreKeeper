package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpacedOutZone(val label: String) {
    Zone1("1"), Zone2("2"), Zone3("3"), Zone4("4"), Zone5("5"),
    Zone6("6"), Zone7("7"), Zone8("8"), Zone9("9"), Zone10("10"),
    Zone11("11"), Zone12("12"), Zone13("13"), Zone14("14"), Zone15("15")
}

data class SpacedOutParticipant(
    val handler: String,
    val dog: String,
    val utn: String
) {
    val displayName: String = buildString {
        if (handler.isNotBlank()) append(handler.trim())
        if (dog.isNotBlank()) {
            if (isNotEmpty()) append(" & ")
            append(dog.trim())
        }
    }.ifBlank { handler.ifBlank { dog.ifBlank { "Unknown Team" } } }
}

data class SpacedOutRoundResult(
    val participant: SpacedOutParticipant,
    val score: Int,
    val spacedOutCount: Int,
    val zonesCaught: Int,
    val misses: Int,
    val ob: Int,
    val sweetSpotBonus: Boolean
)

class SpacedOutScreenModel : ScreenModel {

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

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _spacedOutCount = MutableStateFlow(0)
    val spacedOutCount = _spacedOutCount.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _ob = MutableStateFlow(0)
    val ob = _ob.asStateFlow()

    private val _zonesCaught = MutableStateFlow(0)
    val zonesCaught = _zonesCaught.asStateFlow()

    private val _clickedZonesInRound = MutableStateFlow(emptySet<SpacedOutZone>())
    val clickedZonesInRound = _clickedZonesInRound.asStateFlow()

    private var lastZoneClicked: SpacedOutZone? = null

    private val _sweetSpotBonusOn = MutableStateFlow(false)
    val sweetSpotBonusOn = _sweetSpotBonusOn.asStateFlow()

    private val _fieldFlipped = MutableStateFlow(false)
    val fieldFlipped = _fieldFlipped.asStateFlow()

    private val _activeParticipant = MutableStateFlow<SpacedOutParticipant?>(null)
    val activeParticipant = _activeParticipant.asStateFlow()

    private val _participantQueue = MutableStateFlow<List<SpacedOutParticipant>>(emptyList())
    val participantQueue = _participantQueue.asStateFlow()

    private val _completedResults = MutableStateFlow<List<SpacedOutRoundResult>>(emptyList())
    val completedResults = _completedResults.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries = _logEntries.asStateFlow()

    private val _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning = _timerRunning.asStateFlow()

    private var timerJob: Job? = null
    private var logCounter = 0

    init {
        seedSampleParticipants()
    }

    fun handleZoneClick(zone: SpacedOutZone) {
        if (lastZoneClicked == zone) return

        val points = zonePoints[zone] ?: return
        _score.value += points
        _zonesCaught.value += 1

        if (zone !in _clickedZonesInRound.value) {
            val newClickedZones = _clickedZonesInRound.value + zone
            if (newClickedZones.size == zonePoints.size) {
                awardSpacedOutBonus()
            } else {
                _clickedZonesInRound.value = newClickedZones
            }
        }
        lastZoneClicked = zone
    }

    fun toggleSweetSpotBonus() {
        if (!_sweetSpotBonusOn.value) {
            _score.value += SWEET_SPOT_BONUS_POINTS
            _sweetSpotBonusOn.value = true
        } else {
            _score.value = (_score.value - SWEET_SPOT_BONUS_POINTS).coerceAtLeast(0)
            _sweetSpotBonusOn.value = false
        }
    }

    fun incrementMisses() {
        _misses.value++
        appendLog("Miss recorded")
    }

    fun incrementOb() {
        _ob.value++
        appendLog("OB recorded")
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
        appendLog("Field flipped")
    }

    fun zoneLabel(zone: SpacedOutZone): String = zone.label

    fun zonePoints(zone: SpacedOutZone): Int = zonePoints[zone] ?: 0

    fun reset() {
        stopTimer()
        _score.value = 0
        _spacedOutCount.value = 0
        _misses.value = 0
        _ob.value = 0
        _zonesCaught.value = 0
        resetRoundState()
        _sweetSpotBonusOn.value = false
        appendLog("Round reset")
    }

    fun addParticipant(handler: String, dog: String, utn: String) {
        val participant = SpacedOutParticipant(handler, dog, utn)
        if (_activeParticipant.value == null) {
            _activeParticipant.value = participant
        } else {
            _participantQueue.update { it + participant }
        }
        appendLog("Added ${participant.displayName}")
    }

    fun clearParticipants() {
        _activeParticipant.value = null
        _participantQueue.value = emptyList()
        _completedResults.value = emptyList()
        reset()
        appendLog("Participants cleared")
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
        _participantQueue.value = players.drop(1)
        _activeParticipant.value = players.first()
        // Reset logs/history if needed
        appendLog("Imported ${players.size} teams")
        reset()
    }

    fun exportParticipantsAsCsv(): String {
        val header = "Handler,Dog,UTN,Score,SpacedOut,ZonesCaught,Misses,OB,SweetSpot"
        val rows = buildList {
            _activeParticipant.value?.let { add(buildRoundResult(it)) }
            _participantQueue.value.forEach { participant ->
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
            addAll(_completedResults.value)
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

    fun exportLog(): String = _logEntries.value.joinToString(separator = "\n")

    fun nextParticipant() {
        val active = _activeParticipant.value ?: return
        _completedResults.update { it + buildRoundResult(active) }
        appendLog("Completed ${active.displayName}")
        promoteNextParticipant()
    }

    fun skipParticipant() {
        val active = _activeParticipant.value ?: return
        _participantQueue.update { it + active }
        appendLog("Skipped ${active.displayName}")
        promoteNextParticipant()
    }

    fun previousParticipant() {
        val current = _activeParticipant.value ?: return
        val queue = _participantQueue.value
        if (queue.isEmpty()) return
        val last = queue.last()
        val remaining = queue.dropLast(1)
        _activeParticipant.value = last
        _participantQueue.value = listOf(current) + remaining
        reset()
        appendLog("Moved to previous participant")
    }

    fun startTimer(durationSeconds: Int = DEFAULT_TIMER_SECONDS) {
        if (_timerRunning.value) return
        _timeLeft.value = durationSeconds
        _timerRunning.value = true
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
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

    override fun onDispose() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun awardSpacedOutBonus() {
        _score.value += SPACED_OUT_COMPLETION_BONUS
        _spacedOutCount.value++
        appendLog("Spaced Out achieved")
        resetRoundState()
    }

    private fun resetRoundState() {
        _clickedZonesInRound.value = emptySet()
        lastZoneClicked = null
    }

    private fun promoteNextParticipant() {
        val queue = _participantQueue.value
        if (queue.isEmpty()) {
            _activeParticipant.value = null
            reset()
            return
        }
        _activeParticipant.value = queue.first()
        _participantQueue.value = queue.drop(1)
        reset()
    }

    private fun buildRoundResult(participant: SpacedOutParticipant): SpacedOutRoundResult =
        SpacedOutRoundResult(
            participant = participant,
            score = _score.value,
            spacedOutCount = _spacedOutCount.value,
            zonesCaught = _zonesCaught.value,
            misses = _misses.value,
            ob = _ob.value,
            sweetSpotBonus = _sweetSpotBonusOn.value
        )

    private fun appendLog(message: String) {
        logCounter = (logCounter + 1) % 10000
        val team = _activeParticipant.value?.displayName ?: "No team"
        val entry = "#${logCounter.toString().padStart(4, '0')} [$team] $message"
        _logEntries.update { listOf(entry) + it }
    }

    private fun seedSampleParticipants() {
        val sample = listOf(
            SpacedOutParticipant("Alex Vega", "Bolt", "UTN-001"),
            SpacedOutParticipant("Jamie Reed", "Skye", "UTN-002"),
            SpacedOutParticipant("Morgan Lee", "Nova", "UTN-003")
        )
        _activeParticipant.value = sample.firstOrNull()
        _participantQueue.value = sample.drop(1)
    }

    private companion object {
        private const val SPACED_OUT_COMPLETION_BONUS = 25
        private const val SWEET_SPOT_BONUS_POINTS = 5
        private const val DEFAULT_TIMER_SECONDS = 60
    }
}
