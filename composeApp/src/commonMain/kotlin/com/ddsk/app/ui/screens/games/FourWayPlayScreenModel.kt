package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FourWayPlayScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _clickedZones = MutableStateFlow(emptySet<Int>())
    val clickedZones = _clickedZones.asStateFlow()

    private val _sweetSpotClicked = MutableStateFlow(false)
    val sweetSpotClicked = _sweetSpotClicked.asStateFlow()

    private val _fieldFlipped = MutableStateFlow(false)
    val fieldFlipped = _fieldFlipped.asStateFlow()

    private val _quads = MutableStateFlow(0)
    val quads = _quads.asStateFlow()

    fun handleZoneClick(zone: Int) {
        if (zone !in _clickedZones.value) {
            _score.value += zone
            val newZones = _clickedZones.value + zone

            if (newZones.size == 4) {
                // Quad completed
                _quads.value++
                _clickedZones.value = emptySet() // Reset for the next quad
            } else {
                _clickedZones.value = newZones
            }
        }
    }

    fun handleSweetSpotClick() {
        // Toggle the sweet spot state and adjust score accordingly
        if (!_sweetSpotClicked.value) {
            _score.value += 2
            _sweetSpotClicked.value = true
        } else {
            _score.value -= 2
            _sweetSpotClicked.value = false
        }
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
    }

    fun reset() {
        _score.value = 0
        _clickedZones.value = emptySet()
        _sweetSpotClicked.value = false
        _quads.value = 0
    }
}
