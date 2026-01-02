package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FunKeyZoneType { JUMP, KEY }

class FunKeyScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _isPurpleEnabled = MutableStateFlow(true) // Jumps are purple
    val isPurpleEnabled = _isPurpleEnabled.asStateFlow()

    private val _isBlueEnabled = MutableStateFlow(false)    // Keys are blue
    val isBlueEnabled = _isBlueEnabled.asStateFlow()

    // Counters for each button
    private val _jump1Count = MutableStateFlow(0)
    val jump1Count = _jump1Count.asStateFlow()
    private val _jump2Count = MutableStateFlow(0)
    val jump2Count = _jump2Count.asStateFlow()
    private val _jump3Count = MutableStateFlow(0)
    val jump3Count = _jump3Count.asStateFlow()
    private val _jump2bCount = MutableStateFlow(0)
    val jump2bCount = _jump2bCount.asStateFlow()
    private val _jump3bCount = MutableStateFlow(0)
    val jump3bCount = _jump3bCount.asStateFlow()
    private val _tunnelCount = MutableStateFlow(0)
    val tunnelCount = _tunnelCount.asStateFlow()

    private val _key1Count = MutableStateFlow(0)
    val key1Count = _key1Count.asStateFlow()
    private val _key2Count = MutableStateFlow(0)
    val key2Count = _key2Count.asStateFlow()
    private val _key3Count = MutableStateFlow(0)
    val key3Count = _key3Count.asStateFlow()
    private val _key4Count = MutableStateFlow(0)
    val key4Count = _key4Count.asStateFlow()

    private val _sweetSpotOn = MutableStateFlow(false)
    val sweetSpotOn = _sweetSpotOn.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    fun handleCatch(type: FunKeyZoneType, points: Int, zoneId: String) {
        if (type == FunKeyZoneType.JUMP && _isPurpleEnabled.value) {
            _score.value += points
            updateJumpCounter(zoneId)
            _isPurpleEnabled.value = false
            _isBlueEnabled.value = true
        } else if (type == FunKeyZoneType.KEY && _isBlueEnabled.value) {
            _score.value += points
            updateKeyCounter(zoneId)
            _isBlueEnabled.value = false
            _isPurpleEnabled.value = true
        }
    }

    private fun updateJumpCounter(zoneId: String) {
        when (zoneId) {
            "JUMP1" -> _jump1Count.value++
            "JUMP2" -> _jump2Count.value++
            "JUMP3" -> _jump3Count.value++
            "JUMP2B" -> _jump2bCount.value++
            "JUMP3B" -> _jump3bCount.value++
            "TUNNEL" -> _tunnelCount.value++
        }
    }

    private fun updateKeyCounter(zoneId: String) {
        when (zoneId) {
            "KEY1" -> _key1Count.value++
            "KEY2" -> _key2Count.value++
            "KEY3" -> _key3Count.value++
            "KEY4" -> _key4Count.value++
        }
    }

    fun toggleSweetSpot() {
        if (!_sweetSpotOn.value) {
            _score.value += 2
            _sweetSpotOn.value = true
        } else {
            _score.value -= 2
            _sweetSpotOn.value = false
        }
    }

    fun incrementMisses() {
        _misses.value++
    }

    fun reset() {
        _score.value = 0
        _misses.value = 0
        _isPurpleEnabled.value = true
        _isBlueEnabled.value = false
        _jump1Count.value = 0
        _jump2Count.value = 0
        _jump3Count.value = 0
        _jump2bCount.value = 0
        _jump3bCount.value = 0
        _tunnelCount.value = 0
        _key1Count.value = 0
        _key2Count.value = 0
        _key3Count.value = 0
        _key4Count.value = 0
        _sweetSpotOn.value = false
    }
}
