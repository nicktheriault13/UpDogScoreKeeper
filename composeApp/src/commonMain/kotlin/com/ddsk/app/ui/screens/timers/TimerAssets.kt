package com.ddsk.app.ui.screens.timers

import kotlin.random.Random

sealed interface TimerAsset {
    fun resolve(): String
}

data class SingleAsset(val fileName: String) : TimerAsset {
    override fun resolve(): String = fileName
}

data class RandomAsset(val files: List<String>) : TimerAsset {
    override fun resolve(): String {
        if (files.isEmpty()) return DEFAULT_TIMER_ASSET
        val idx = Random.nextInt(files.size)
        return files[idx]
    }
}

const val DEFAULT_TIMER_ASSET = "assets/audio/sixty_seconds_timer.mp3"

data class TimerGameDef(val name: String, val asset: TimerAsset)

val SharedTimerGames = listOf(
    TimerGameDef("15s Timer", SingleAsset("assets/audio/15_Second_Timer.mp3")),
    TimerGameDef("4-Way Play", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("7-Up", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Boom", SingleAsset("assets/audio/Boom Timer 60 sec.mp3")),
    TimerGameDef("Far Out", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef(
        "Fire Ball",
        RandomAsset(
            listOf(
                "assets/audio/fireball/Fireball Timer 1.mp3",
                "assets/audio/fireball/Fireball Timer 2.mp3",
                "assets/audio/fireball/Fireball Timer 3.mp3",
                "assets/audio/fireball/Fireball Timer 4.mp3",
                "assets/audio/fireball/Fireball Timer 5.mp3",
                "assets/audio/fireball/Fireball Timer 6.mp3"
            )
        )
    ),
    TimerGameDef("Frizgility", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Frizgility L2", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Fun Key", SingleAsset("assets/audio/FunKey 75 seconds JF.mp3")),
    TimerGameDef("Greedy", SingleAsset("assets/audio/FunKey 75 seconds JF.mp3")),
    TimerGameDef("Spaced Out", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Spaced Out L2", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Throw N Go", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Throw N Go L2", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Time Warp", SingleAsset(DEFAULT_TIMER_ASSET)),
    TimerGameDef("Time Warp L2", SingleAsset(DEFAULT_TIMER_ASSET))
)

fun getTimerAssetForGame(gameName: String): String {
    // Exact match
    SharedTimerGames.find { it.name == gameName }?.let { return it.asset.resolve() }

    // Fuzzy/Prefix match if needed, or default
    return DEFAULT_TIMER_ASSET
}
