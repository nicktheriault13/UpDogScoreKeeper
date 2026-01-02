package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.TimeSource

class TimeWarpScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _ob = MutableStateFlow(0)
    val ob = _ob.asStateFlow()

    private val _clickedZones = MutableStateFlow(emptySet<Int>())
    val clickedZones = _clickedZones.asStateFlow()

    private val _sweetSpotClicked = MutableStateFlow(false)
    val sweetSpotClicked = _sweetSpotClicked.asStateFlow()

    private val _allRollersClicked = MutableStateFlow(false)
    val allRollersClicked = _allRollersClicked.asStateFlow()

    private val _fieldFlipped = MutableStateFlow(false)
    val fieldFlipped = _fieldFlipped.asStateFlow()

    private val _timeRemaining = MutableStateFlow(60.0f)
    val timeRemaining = _timeRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null
    private var timePointsAdded = false
    private var startTimeMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var initialTimeOnStart = 60.0f

    fun handleZoneClick(zone: Int) {
        if (zone !in _clickedZones.value) {
            _score.value += 5
            _clickedZones.value += zone
        }
    }

    fun handleSweetSpotClick() {
        if (_clickedZones.value.size == 3) { // Only allow if all 3 zones are clicked
            if (!_sweetSpotClicked.value) {
                _score.value += 25
                _sweetSpotClicked.value = true
            } else {
                _score.value -= 25
                _sweetSpotClicked.value = false
            }
        }
    }

    fun startTimer() {
        if (!_isTimerRunning.value) {
            _isTimerRunning.value = true
            startTimeMark = TimeSource.Monotonic.markNow()
            initialTimeOnStart = _timeRemaining.value

            timerJob = screenModelScope.launch {
                while (_isTimerRunning.value) {
                    startTimeMark?.let { mark ->
                        val elapsedSeconds = mark.elapsedNow().inWholeMilliseconds / 1000f
                        val newTime = initialTimeOnStart - elapsedSeconds
                        _timeRemaining.value = max(0f, newTime)
                    }

                    if (_timeRemaining.value <= 0f) {
                        stopTimer()
                    }
                    delay(10L) // Update UI frequently
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        if (!timePointsAdded) {
            _score.value += _timeRemaining.value.roundToInt()
            timePointsAdded = true
        }
    }

    fun setTimeManually(timeStr: String) {
        val newTime = timeStr.toFloatOrNull() ?: return
        timerJob?.cancel()
        _isTimerRunning.value = false
        _timeRemaining.value = newTime
        if (!timePointsAdded) {
            _score.value += newTime.roundToInt()
            timePointsAdded = true
        }
    }

    fun incrementMisses() {
        _misses.value++
    }

    fun incrementOb() {
        _ob.value++
    }

    fun toggleAllRollers() {
        _allRollersClicked.value = !_allRollersClicked.value
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
    }

    fun reset() {
        _score.value = 0
        _misses.value = 0
        _ob.value = 0
        _clickedZones.value = emptySet()
        _sweetSpotClicked.value = false
        _allRollersClicked.value = false
        _timeRemaining.value = 60.0f
        timerJob?.cancel()
        _isTimerRunning.value = false
        timePointsAdded = false
    }
}
