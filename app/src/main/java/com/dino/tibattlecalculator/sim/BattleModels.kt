package com.dino.tibattlecalculator.sim

import com.dino.tibattlecalculator.domain.ShipType

// CHANGED: upgrades -> upgradedShips (typed) and added factionCombatModifier
data class FleetConfig(
    val name: String,
    val factionId: String?,
    val ships: Map<ShipType, Int>,
    val upgradedShips: Set<ShipType> = emptySet(),
    val factionCombatModifier: Int = 0
)

data class BattleConfig(
    val player1: FleetConfig,
    val player2: FleetConfig,
    val simulations: Int
)

data class BattleResult(
    val p1WinRate: Double,
    val p2WinRate: Double,
    val drawRate: Double,
    val avgLossesP1: Map<ShipType, Double>,
    val avgLossesP2: Map<ShipType, Double>,
    val avgResourceLossP1: Double = 0.0,
    val avgResourceLossP2: Double = 0.0
)


