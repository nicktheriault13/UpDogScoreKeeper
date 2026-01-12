package com.ddsk.app.ui.screens.games

import kotlinx.serialization.Serializable

@Serializable
data class GreedyParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val heightDivision: String = "",
    val zone1Catches: Int = 0,
    val zone2Catches: Int = 0,
    val zone3Catches: Int = 0,
    val zone4Catches: Int = 0,
    val finishOnSweetSpot: Boolean = false,
    val sweetSpotBonus: Int = 0,
    val numberOfMisses: Int = 0,
    val allRollers: Boolean = false,
    val score: Int = 0
)

@Serializable
data class FarOutParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val jumpHeight: String = "",
    val heightDivision: String = "",
    val clubDivision: String = "",
    val throw1: String = "",
    val throw2: String = "",
    val throw3: String = "",
    val sweetShot: String = "",
    val sweetShotDeclined: Boolean = false,
    val allRollers: Boolean = false,
    val throw1Miss: Boolean = false,
    val throw2Miss: Boolean = false,
    val throw3Miss: Boolean = false,
    val sweetShotMiss: Boolean = false,
    val score: Double = 0.0,
    val misses: Int = 0
)

@Serializable
data class TimeWarpRoundResult(
    val score: Int,
    val timeRemaining: Float,
    val misses: Int,
    val zonesCaught: Int,
    val sweetSpot: Boolean,
    val allRollers: Boolean
)

@Serializable
data class TimeWarpParticipant(
    val handler: String,
    val dog: String,
    val utn: String,
    val result: TimeWarpRoundResult? = null
) {
    val displayName: String get() = buildString {
        append(handler.ifBlank { "Unknown Handler" })
        if (dog.isNotBlank()) {
            append(" & ")
            append(dog)
        }
    }
}
