package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GreedyScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _throwZone = MutableStateFlow(1)
    val throwZone = _throwZone.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees = _rotationDegrees.asStateFlow()

    private val _sweetSpotBonus = MutableStateFlow(0)
    val sweetSpotBonus = _sweetSpotBonus.asStateFlow()

    private val _activeButtonsByZone = MutableStateFlow<List<MutableSet<String>>>(List(5) { mutableSetOf() })
    val activeButtonsByZone = _activeButtonsByZone.asStateFlow()

    fun handleButtonPress(button: String) {
        val currentZone = _throwZone.value
        if (currentZone in 1..4) {
            val zoneButtons = _activeButtonsByZone.value[currentZone]
            if (button !in zoneButtons) {
                zoneButtons.add(button)
                _activeButtonsByZone.value = _activeButtonsByZone.value // Trigger recomposition

                if (currentZone == 4 && button == "Sweet Spot") {
                    calculateFinalScore()
                }
            }
        }
    }

    fun nextThrowZone(clockwise: Boolean) {
        if (_throwZone.value < 4) {
            _throwZone.value++
            if (clockwise) {
                _rotationDegrees.value += 90
            } else {
                _rotationDegrees.value -= 90
            }
        }
    }

    fun setSweetSpotBonus(bonus: Int) {
        if (bonus in 1..8) {
            _sweetSpotBonus.value = if(bonus == 8) 10 else bonus // 8 gets a +2 bonus
        }
    }

    fun incrementMisses() {
        _misses.value++
    }

    private fun calculateFinalScore() {
        var totalScore = 0
        for (zone in 1..4) {
            val zonePoints = when (zone) {
                1 -> 5
                2 -> 4
                3 -> 3
                4 -> 2
                else -> 0
            }
            // Score is based on the number of unique buttons clicked in each zone
            totalScore += _activeButtonsByZone.value[zone].size * zonePoints
        }
        totalScore += _sweetSpotBonus.value
        _score.value = totalScore
    }

    fun reset() {
        _score.value = 0
        _throwZone.value = 1
        _misses.value = 0
        _rotationDegrees.value = 0
        _sweetSpotBonus.value = 0
        _activeButtonsByZone.value = List(5) { mutableSetOf() }
    }
}
