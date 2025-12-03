package com.dino.tibattlecalculator.domain

import android.graphics.RectF

enum class ShipType(val yoloClassId: Int, val displayName: String) {
    Carrier(0, "Carrier"),
    Cruiser(1, "Cruiser"),
    Destroyer(2, "Destroyer"),
    Dreadnought(3, "Dreadnought"),
    Fighter(4, "Fighter"),
    WarSun(6, "War Sun"),
    Flagship(5, "Flagship"),
    Unknown(-1, "Unknown");

    companion object {
        fun fromYoloClassId(id: Int): ShipType =
            entries.firstOrNull { it.yoloClassId == id } ?: Unknown
    }
}

enum class PlayerColor(val displayName: String) {
    Red("Red"),
    Orange("Orange"),
    Yellow("Yellow"),
    Blue("Blue"),
    Purple("Purple"),
    Black("Black"),
    Unknown("Unknown")
}


data class DetectedShip(
    val shipType: ShipType,
    val playerColor: PlayerColor,
    val confidence: Float,
    val bbox: RectF
)
