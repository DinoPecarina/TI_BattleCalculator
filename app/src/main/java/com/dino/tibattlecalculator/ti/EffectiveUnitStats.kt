package com.dino.tibattlecalculator.ti

import com.dino.tibattlecalculator.domain.ShipType

data class EffectiveUnitStats(
    val id: String,
    val shipType: ShipType,
    val displayName: String,
    val spaceDice: Int,
    val spaceHitOn: Int,
    val hasSustainDamage: Boolean,
    val antiFighterDice: Int,
    val antiFighterHitOn: Int,
    val costResources: Int
)
