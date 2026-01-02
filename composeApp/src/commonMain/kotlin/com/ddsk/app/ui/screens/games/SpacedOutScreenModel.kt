package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpacedOutZone { ZONE_1, ZONE_2, ZONE_3, SWEET_SPOT_GRID }

class SpacedOutScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _spacedOutCount = MutableStateFlow(0)
    val spacedOutCount = _spacedOutCount.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _ob = MutableStateFlow(0)
    val ob = _ob.asStateFlow()

    private val _clickedZonesInRound = MutableStateFlow(emptySet<SpacedOutZone>())
    val clickedZonesInRound = _clickedZonesInRound.asStateFlow()

    private var lastZoneClicked: SpacedOutZone? = null

    private val _sweetSpotBonusOn = MutableStateFlow(false)
    val sweetSpotBonusOn = _sweetSpotBonusOn.asStateFlow()

    private val _fieldFlipped = MutableStateFlow(false)
    val fieldFlipped = _fieldFlipped.asStateFlow()

    fun handleZoneClick(zone: SpacedOutZone) {
        if (lastZoneClicked == zone) return // Prevent double clicks

        _score.value += 5

        if (zone !in _clickedZonesInRound.value) {
            val newClickedZones = _clickedZonesInRound.value + zone
            if (newClickedZones.size == 4) {
                // Spaced Out achieved
                _score.value += 25
                _spacedOutCount.value++
                _clickedZonesInRound.value = emptySet() // Reset for next Spaced Out
            } else {
                _clickedZonesInRound.value = newClickedZones
            }
        }
        lastZoneClicked = zone
    }

    fun toggleSweetSpotBonus() {
        if (!_sweetSpotBonusOn.value) {
            _score.value += 5
            _sweetSpotBonusOn.value = true
        } else {
            _score.value -= 5
            _sweetSpotBonusOn.value = false
        }
    }

    fun incrementMisses() {
        _misses.value++
    }

    fun incrementOb() {
        _ob.value++
    }

    fun flipField() {
        _fieldFlipped.value = !_fieldFlipped.value
    }

    fun reset() {
        _score.value = 0
        _spacedOutCount.value = 0
        _misses.value = 0
        _ob.value = 0
        _clickedZonesInRound.value = emptySet()
        lastZoneClicked = null
        _sweetSpotBonusOn.value = false
    }
}
