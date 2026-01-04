package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpacedOutZone { ZONE_1, ZONE_2, ZONE_3, SWEET_SPOT_GRID }

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

expect fun parseXlsxRows(bytes: ByteArray): List<List<String>>

class SpacedOutScreenModel : ScreenModel {

    private data class ZoneDescriptor(val label: String, val points: Int)

    private val zoneDescriptors = mapOf(
        SpacedOutZone.ZONE_1 to ZoneDescriptor(label = "Zone 1", points = 5),
        SpacedOutZone.ZONE_2 to ZoneDescriptor(label = "Zone 2", points = 5),
        SpacedOutZone.ZONE_3 to ZoneDescriptor(label = "Zone 3", points = 5),
        SpacedOutZone.SWEET_SPOT_GRID to ZoneDescriptor(label = "Sweet Spot", points = 5)
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

        val descriptor = zoneDescriptors[zone] ?: return
        _score.value += descriptor.points
        _zonesCaught.value += 1

        if (zone !in _clickedZonesInRound.value) {
            val newClickedZones = _clickedZonesInRound.value + zone
            if (newClickedZones.size == zoneDescriptors.size) {
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

    fun zoneLabel(zone: SpacedOutZone): String = zoneDescriptors[zone]?.label ?: zone.name

    fun zonePoints(zone: SpacedOutZone): Int = zoneDescriptors[zone]?.points ?: 0

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
        val rows = parseCsvRows(csvText)
        val participants = buildParticipantsFromRows(rows)
        applyImportedParticipants(participants, "CSV")
    }

    fun importParticipantsFromXlsx(xlsxData: ByteArray) {
        val rows = runCatching { parseXlsxParticipantRows(xlsxData) }.getOrElse { emptyList() }
        val participants = buildParticipantsFromRows(rows)
        applyImportedParticipants(participants, "XLSX")
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

    private fun applyImportedParticipants(
        participants: List<SpacedOutParticipant>,
        sourceLabel: String
    ) {
        if (participants.isEmpty()) {
            appendLog("$sourceLabel import found no valid rows")
            return
        }
        _activeParticipant.value = participants.first()
        _participantQueue.value = participants.drop(1)
        _completedResults.value = emptyList()
        reset()
        appendLog("Imported ${participants.size} participant(s) via $sourceLabel")
    }

    private fun buildParticipantsFromRows(rows: List<List<String>>): List<SpacedOutParticipant> {
        if (rows.isEmpty()) return emptyList()
        val dataRows = if (hasHeaderRow(rows.first())) rows.drop(1) else rows
        return dataRows.mapNotNull { columns ->
            val handler = columns.getOrNull(0)?.trim().orEmpty()
            val dog = columns.getOrNull(1)?.trim().orEmpty()
            val utn = columns.getOrNull(2)?.trim().orEmpty()
            if (handler.isBlank() && dog.isBlank()) null else SpacedOutParticipant(handler, dog, utn)
        }
    }

    private fun hasHeaderRow(columns: List<String>): Boolean {
        if (columns.isEmpty()) return false
        val normalized = columns.map { it.lowercase() }
        return normalized.any { keyword -> HEADER_KEYWORDS.any { keyword.contains(it) } }
    }

    private fun parseCsvRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        csvText.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .forEach { rows += splitCsvLine(it) }
        return rows
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result += current.toString().trim().trim('"')
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString().trim().trim('"')
        return result
    }

    private fun parseXlsxParticipantRows(xlsx: ByteArray): List<List<String>> = parseXlsxRows(xlsx)

    private fun String.unescapeXmlEntities(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    private companion object {
        private const val SPACED_OUT_COMPLETION_BONUS = 25
        private const val SWEET_SPOT_BONUS_POINTS = 5
        private const val DEFAULT_TIMER_SECONDS = 60
        private val HEADER_KEYWORDS = listOf("handler", "dog", "utn")
    }
}
