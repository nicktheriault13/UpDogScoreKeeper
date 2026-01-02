package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.TimeSource

class SevenUpScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    // Jump tracking
    private val _jumpCounts = MutableStateFlow(mapOf<String, Int>()) 
    val jumpCounts = _jumpCounts.asStateFlow()
    private val _disabledJumps = MutableStateFlow(emptySet<String>())
    val disabledJumps = _disabledJumps.asStateFlow()
    private val _jumpStreak = MutableStateFlow(0)
    val jumpStreak = _jumpStreak.asStateFlow()

    // Non-jump tracking
    private val _markedCells = MutableStateFlow(mapOf<String, Int>())
    val markedCells = _markedCells.asStateFlow()
    private val _nonJumpMark = MutableStateFlow(1)
    val nonJumpMark = _nonJumpMark.asStateFlow()
    private val _lastWasNonJump = MutableStateFlow(false)
    val lastWasNonJump = _lastWasNonJump.asStateFlow()
    private val _sweetSpotBonus = MutableStateFlow(false)
    val sweetSpotBonus = _sweetSpotBonus.asStateFlow()

    private val _hasStarted = MutableStateFlow(false)
    val hasStarted = _hasStarted.asStateFlow()

    // Layout and Timer
    private val _rectangleVersion = MutableStateFlow(0)
    val rectangleVersion = _rectangleVersion.asStateFlow()
    private val _isFlipped = MutableStateFlow(false)
    val isFlipped = _isFlipped.asStateFlow()
    private val _timeRemaining = MutableStateFlow(60.0f)
    val timeRemaining = _timeRemaining.asStateFlow()
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private var timerJob: Job? = null
    private var startTimeMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var initialTimeOnStart = 60.0f

    fun handleCellPress(label: String, row: Int, col: Int) {
        val cellKey = "$row,$col"
        if (label.startsWith("Jump")) {
            if (!_hasStarted.value) _hasStarted.value = true
            if (label in _disabledJumps.value) return
            if (_jumpStreak.value >= 3) return

            _jumpStreak.value++
            _disabledJumps.value += label
            _jumpCounts.value += (label to (_jumpCounts.value[label] ?: 0) + 1)
            _score.value += 3
            _lastWasNonJump.value = false

        } else { // Non-jump cell
            if (!_hasStarted.value || _lastWasNonJump.value) return
            if (cellKey in _markedCells.value) return
            if (_nonJumpMark.value > 5) return

            _markedCells.value += (cellKey to _nonJumpMark.value)
            
            if (_nonJumpMark.value == 5 && label == "Sweet Spot") {
                _score.value += 8 // 1 for non-jump + 7 bonus
                _sweetSpotBonus.value = true
            } else {
                _score.value += 1
            }

            _nonJumpMark.value++
            _jumpStreak.value = 0
            _disabledJumps.value = emptySet()
            _lastWasNonJump.value = true
        }
    }

    fun startTimer() {
        if (!_isTimerRunning.value) {
            _isTimerRunning.value = true
            startTimeMark = TimeSource.Monotonic.markNow()
            initialTimeOnStart = _timeRemaining.value
            timerJob = screenModelScope.launch {
                while (_isTimerRunning.value) {
                    val elapsedSeconds = startTimeMark!!.elapsedNow().inWholeMilliseconds / 1000f
                    _timeRemaining.value = max(0f, initialTimeOnStart - elapsedSeconds)
                    if (_timeRemaining.value <= 0f) stopTimer()
                    delay(10L)
                }
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        _score.value += _timeRemaining.value.toInt()
    }

    fun setTimeManually(timeStr: String) {
        val newTime = timeStr.toFloatOrNull() ?: return
        timerJob?.cancel()
        _isTimerRunning.value = false
        _timeRemaining.value = newTime
        _score.value += newTime.toInt()
    }

    fun cycleRectangleVersion() {
        if (!_hasStarted.value) {
            _rectangleVersion.value = (_rectangleVersion.value + 1) % 11 // 11 versions from the code
        }
    }

    fun flipField() {
        _isFlipped.value = !_isFlipped.value
    }

    fun reset() {
        _score.value = 0
        _jumpCounts.value = emptyMap()
        _disabledJumps.value = emptySet()
        _jumpStreak.value = 0
        _hasStarted.value = false
        _markedCells.value = emptyMap()
        _nonJumpMark.value = 1
        _lastWasNonJump.value = false
        _sweetSpotBonus.value = false
        _timeRemaining.value = 60.0f
        timerJob?.cancel()
        _isTimerRunning.value = false
    }
}
