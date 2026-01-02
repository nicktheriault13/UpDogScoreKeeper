package com.ddsk.app.ui.screens.games

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThrowNGoScreenModel : ScreenModel {

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _misses = MutableStateFlow(0)
    val misses = _misses.asStateFlow()

    private val _ob = MutableStateFlow(0)
    val ob = _ob.asStateFlow()

    private val _catches = MutableStateFlow(0)
    val catches = _catches.asStateFlow()

    fun handleCatch(points: Int) {
        _score.value += points
        _catches.value++
    }

    fun incrementMisses() {
        _misses.value++
    }

    fun incrementOb() {
        _ob.value++
        _catches.value++ // As per the logic, OB also counts as a catch
    }

    fun reset() {
        _score.value = 0
        _misses.value = 0
        _ob.value = 0
        _catches.value = 0
    }
}
