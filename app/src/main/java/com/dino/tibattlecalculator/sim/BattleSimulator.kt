package com.dino.tibattlecalculator.sim

import com.dino.tibattlecalculator.domain.ShipType
import com.dino.tibattlecalculator.ti.EffectiveUnitStats
import com.dino.tibattlecalculator.ti.UnitStatsRepository
import kotlin.random.Random

class BattleSimulator(
    private val random: Random = Random.Default
) {

    fun simulate(config: BattleConfig, unitRepo: UnitStatsRepository): BattleResult {
        val p1Wins = IntArray(1)
        val p2Wins = IntArray(1)
        val draws = IntArray(1)

        val totalLossesP1 = mutableMapOf<ShipType, Int>()
        val totalLossesP2 = mutableMapOf<ShipType, Int>()

        var totalResourceLossP1 = 0.0
        var totalResourceLossP2 = 0.0

        repeat(config.simulations) {
            val outcome = simulateSingle(config, unitRepo)

            when (outcome.winner) {
                Winner.P1 -> p1Wins[0]++
                Winner.P2 -> p2Wins[0]++
                Winner.Draw -> draws[0]++
            }

            outcome.lossesP1.forEach { (type, loss) ->
                totalLossesP1[type] = (totalLossesP1[type] ?: 0) + loss
            }
            outcome.lossesP2.forEach { (type, loss) ->
                totalLossesP2[type] = (totalLossesP2[type] ?: 0) + loss
            }

            var resourceLossP1 = 0.0
            var resourceLossP2 = 0.0

            outcome.lossesP1.forEach { (type, loss) ->
                if (loss <= 0) return@forEach
                val upgraded = type in config.player1.upgradedShips
                val cost = unitRepo.getUnitResourceCost(type, config.player1.factionId, upgraded)
                resourceLossP1 += cost * loss
            }

            outcome.lossesP2.forEach { (type, loss) ->
                if (loss <= 0) return@forEach
                val upgraded = type in config.player2.upgradedShips
                val cost = unitRepo.getUnitResourceCost(type, config.player2.factionId, upgraded)
                resourceLossP2 += cost * loss
            }

            totalResourceLossP1 += resourceLossP1
            totalResourceLossP2 += resourceLossP2
        }

        val sims = config.simulations.toDouble()

        val avgLossesP1 = totalLossesP1.mapValues { it.value / sims }
        val avgLossesP2 = totalLossesP2.mapValues { it.value / sims }

        val avgResourceLossP1 = totalResourceLossP1 / sims
        val avgResourceLossP2 = totalResourceLossP2 / sims

        return BattleResult(
            p1WinRate = p1Wins[0] / sims,
            p2WinRate = p2Wins[0] / sims,
            drawRate = draws[0] / sims,
            avgLossesP1 = avgLossesP1,
            avgLossesP2 = avgLossesP2,
            avgResourceLossP1 = avgResourceLossP1,
            avgResourceLossP2 = avgResourceLossP2
        )
    }

    private enum class Winner { P1, P2, Draw }

    private data class FleetState(
        val units: MutableList<UnitInstance>
    ) {
        fun isDead(): Boolean = units.isEmpty()
    }

    private data class UnitInstance(
        val stats: EffectiveUnitStats,
        val combatModifier: Int,
        var damaged: Boolean = false
    )

    private data class SingleBattleOutcome(
        val winner: Winner,
        val lossesP1: Map<ShipType, Int>,
        val lossesP2: Map<ShipType, Int>
    )

    private fun simulateSingle(
        config: BattleConfig,
        unitRepo: UnitStatsRepository
    ): SingleBattleOutcome {
        val p1Fleet = buildFleetState(config.player1, unitRepo)
        val p2Fleet = buildFleetState(config.player2, unitRepo)

        val initialCountsP1 = countByType(p1Fleet)
        val initialCountsP2 = countByType(p2Fleet)

        resolveAntiFighterBarrage(p1Fleet, p2Fleet)
        resolveAntiFighterBarrage(p2Fleet, p1Fleet)

        if (p1Fleet.isDead() && p2Fleet.isDead()) {
            return SingleBattleOutcome(
                winner = Winner.Draw,
                lossesP1 = initialCountsP1,
                lossesP2 = initialCountsP2
            )
        } else if (p1Fleet.isDead()) {
            return SingleBattleOutcome(
                winner = Winner.P2,
                lossesP1 = initialCountsP1,
                lossesP2 = diffLosses(initialCountsP2, p2Fleet)
            )
        } else if (p2Fleet.isDead()) {
            return SingleBattleOutcome(
                winner = Winner.P1,
                lossesP1 = diffLosses(initialCountsP1, p1Fleet),
                lossesP2 = initialCountsP2
            )
        }

        while (!p1Fleet.isDead() && !p2Fleet.isDead()) {
            val hitsOnP1 = rollSpaceCombatHits(p2Fleet)
            val hitsOnP2 = rollSpaceCombatHits(p1Fleet)

            applyHits(p1Fleet, hitsOnP1)
            applyHits(p2Fleet, hitsOnP2)

            if (p1Fleet.isDead() && p2Fleet.isDead()) {
                return SingleBattleOutcome(
                    winner = Winner.Draw,
                    lossesP1 = initialCountsP1,
                    lossesP2 = initialCountsP2
                )
            } else if (p1Fleet.isDead()) {
                return SingleBattleOutcome(
                    winner = Winner.P2,
                    lossesP1 = initialCountsP1,
                    lossesP2 = diffLosses(initialCountsP2, p2Fleet)
                )
            } else if (p2Fleet.isDead()) {
                return SingleBattleOutcome(
                    winner = Winner.P1,
                    lossesP1 = diffLosses(initialCountsP1, p1Fleet),
                    lossesP2 = initialCountsP2
                )
            }
        }

        return SingleBattleOutcome(
            winner = Winner.Draw,
            lossesP1 = initialCountsP1,
            lossesP2 = initialCountsP2
        )
    }

    private fun buildFleetState(
        cfg: FleetConfig,
        unitRepo: UnitStatsRepository
    ): FleetState {
        val units = mutableListOf<UnitInstance>()

        cfg.ships.forEach { (shipType, count) ->
            if (count <= 0) return@forEach

            val isUpgraded = shipType in cfg.upgradedShips
            val stats = unitRepo.getEffectiveStatsForBattle(
                shipType = shipType,
                factionId = cfg.factionId,
                isUpgraded = isUpgraded
            )

            repeat(count) {
                units += UnitInstance(
                    stats = stats,
                    combatModifier = cfg.factionCombatModifier
                )
            }
        }

        return FleetState(units)
    }

    private fun rollSpaceCombatHits(fleet: FleetState): Int {
        var hits = 0
        for (unit in fleet.units) {
            repeat(unit.stats.spaceDice) {
                val roll = random.nextInt(1, 11)
                val totalRoll = roll + unit.combatModifier
                if (totalRoll >= unit.stats.spaceHitOn) hits++
            }
        }
        return hits
    }

    private fun resolveAntiFighterBarrage(
        attacker: FleetState,
        defender: FleetState
    ) {
        if (attacker.units.isEmpty() || defender.units.isEmpty()) return

        val afbUnits = attacker.units.filter { it.stats.antiFighterDice > 0 }
        if (afbUnits.isEmpty()) return

        var hits = 0
        for (unit in afbUnits) {
            repeat(unit.stats.antiFighterDice) {
                val roll = random.nextInt(1, 11)
                if (roll >= unit.stats.antiFighterHitOn) hits++
            }
        }

        if (hits <= 0) return

        val fighters = defender.units.filter { it.stats.shipType == ShipType.Fighter }
        val toKill = minOf(hits, fighters.size)
        repeat(toKill) {
            val idx = defender.units.indexOfFirst { it.stats.shipType == ShipType.Fighter }
            if (idx >= 0) defender.units.removeAt(idx)
        }
    }

    private fun applyHits(fleet: FleetState, hits: Int) {
        var remainingHits = hits

        if (remainingHits <= 0 || fleet.units.isEmpty()) return

        val sustainables = fleet.units.filter { it.stats.hasSustainDamage && !it.damaged }
        val sustainUsed = minOf(remainingHits, sustainables.size)
        for (i in 0 until sustainUsed) {
            sustainables[i].damaged = true
        }
        remainingHits -= sustainUsed

        if (remainingHits <= 0) return

        val killOrder = listOf(
            ShipType.Fighter,
            ShipType.Destroyer,
            ShipType.Cruiser,
            ShipType.Carrier,
            ShipType.Dreadnought,
            ShipType.Flagship,
            ShipType.WarSun
        )

        for (type in killOrder) {
            if (remainingHits <= 0) break

            while (remainingHits > 0) {
                val idx = fleet.units.indexOfFirst { it.stats.shipType == type }
                if (idx == -1) break
                fleet.units.removeAt(idx)
                remainingHits--
            }
        }
    }

    private fun countByType(fleet: FleetState): Map<ShipType, Int> =
        fleet.units.groupingBy { it.stats.shipType }.eachCount()

    private fun diffLosses(
        initial: Map<ShipType, Int>,
        current: FleetState
    ): Map<ShipType, Int> {
        val currentCounts = countByType(current)
        return initial.mapValues { (type, initialCount) ->
            val now = currentCounts[type] ?: 0
            initialCount - now
        }
    }
}
