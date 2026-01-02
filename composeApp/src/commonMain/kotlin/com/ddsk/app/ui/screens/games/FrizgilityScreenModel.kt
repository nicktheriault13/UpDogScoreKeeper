package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrizgilityScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _obstacle1Count = MutableStateFlow(0)
    val obstacle1Count = _obstacle1Count.asStateFlow()
    private val _obstacle2Count = MutableStateFlow(0)
    val obstacle2Count = _obstacle2Count.asStateFlow()
    private val _obstacle3Count = MutableStateFlow(0)
    val obstacle3Count = _obstacle3Count.asStateFlow()

    private val _fail1Count = MutableStateFlow(0)
    val fail1Count = _fail1Count.asStateFlow()
    private val _fail2Count = MutableStateFlow(0)
    val fail2Count = _fail2Count.asStateFlow()
    private val _fail3Count = MutableStateFlow(0)
    val fail3Count = _fail3Count.asStateFlow()

    private val _catch3to10Count = MutableStateFlow(0)
    val catch3to10Count = _catch3to10Count.asStateFlow()
    private val _catch10plusCount = MutableStateFlow(0)
    val catch10plusCount = _catch10plusCount.asStateFlow()

    private val _missCount = MutableStateFlow(0)
    val missCount = _missCount.asStateFlow()

    private val _missClicksInPhase = MutableStateFlow(0)
    val missClicksInPhase = _missClicksInPhase.asStateFlow()

    private val _sweetSpotActive = MutableStateFlow(false)
    val sweetSpotActive = _sweetSpotActive.asStateFlow()

    private val _obstaclePhaseActive = MutableStateFlow(true)
    val obstaclePhaseActive = _obstaclePhaseActive.asStateFlow()
    private val _catchPhaseActive = MutableStateFlow(false)
    val catchPhaseActive = _catchPhaseActive.asStateFlow()

    private val _obstacleOrFailClicked = MutableStateFlow(emptySet<Int>())
    val obstacleOrFailClicked = _obstacleOrFailClicked.asStateFlow()
    private val _catchClicked = MutableStateFlow(emptySet<Int>())
    val catchClicked = _catchClicked.asStateFlow()

    private fun updateScore() {
        val obstacleScore = (_obstacle1Count.value * 5) + (_obstacle2Count.value * 5) + (_obstacle3Count.value * 5)
        val catchScore = (_catch3to10Count.value * 3) + (_catch10plusCount.value * 10)
        val sweetSpotScore = if (_sweetSpotActive.value) 10 else 0
        _score.value = obstacleScore + catchScore + sweetSpotScore
    }

    fun handleObstacleClick(obstacleNumber: Int) {
        if (!_obstaclePhaseActive.value) return

        if (_catchPhaseActive.value) { // In hybrid phase, this action ends the round
            when (obstacleNumber) {
                1 -> _obstacle1Count.value++
                2 -> _obstacle2Count.value++
                3 -> _obstacle3Count.value++
            }
            updateScore()
            startNewRound()
        } else { // Normal obstacle phase
            if (obstacleNumber in _obstacleOrFailClicked.value) return
            when (obstacleNumber) {
                1 -> _obstacle1Count.value++
                2 -> _obstacle2Count.value++
                3 -> _obstacle3Count.value++
            }
            _obstacleOrFailClicked.value += obstacleNumber
            updateScore()
            checkPhaseTransition()
        }
    }

    fun handleFailClick(failNumber: Int) {
        if (!_obstaclePhaseActive.value) return

        if (_catchPhaseActive.value) { // In hybrid phase, this action ends the round
            when (failNumber) {
                1 -> _fail1Count.value++
                2 -> _fail2Count.value++
                3 -> _fail3Count.value++
            }
            startNewRound()
        } else { // Normal obstacle phase
            if (failNumber in _obstacleOrFailClicked.value) return
            when (failNumber) {
                1 -> _fail1Count.value++
                2 -> _fail2Count.value++
                3 -> _fail3Count.value++
            }
            _obstacleOrFailClicked.value += failNumber
            checkPhaseTransition()
        }
    }

    fun handleCatchClick(points: Int) {
        if (!_catchPhaseActive.value || points in _catchClicked.value) return

        when (points) {
            3 -> _catch3to10Count.value++
            10 -> _catch10plusCount.value++
        }
        updateScore()
        startNewRound()
    }

    fun handleMissClick() {
        if (_catchPhaseActive.value && _missClicksInPhase.value < 3) {
            _missCount.value++
            _missClicksInPhase.value++
            if (_missClicksInPhase.value == 1) {
                // After 1st miss, enter hybrid phase by enabling obstacle buttons and resetting them
                _obstaclePhaseActive.value = true
                _obstacleOrFailClicked.value = emptySet()
            }
            if (_missClicksInPhase.value >= 3) {
                // After 3rd miss, end the round
                startNewRound()
            }
        }
    }

    private fun checkPhaseTransition() {
        if (_obstacleOrFailClicked.value.size == 3) {
            _obstaclePhaseActive.value = false
            _catchPhaseActive.value = true
        }
    }

    private fun startNewRound() {
        _catchPhaseActive.value = false
        _obstaclePhaseActive.value = true
        _obstacleOrFailClicked.value = emptySet()
        _catchClicked.value = emptySet()
        _missClicksInPhase.value = 0
    }

    fun toggleSweetSpot() {
        _sweetSpotActive.value = !_sweetSpotActive.value
        updateScore()
    }

    fun reset() {
        _obstacle1Count.value = 0
        _obstacle2Count.value = 0
        _obstacle3Count.value = 0
        _fail1Count.value = 0
        _fail2Count.value = 0
        _fail3Count.value = 0
        _catch3to10Count.value = 0
        _catch10plusCount.value = 0
        _missCount.value = 0
        _sweetSpotActive.value = false
        startNewRound()
        updateScore()
    }
}
