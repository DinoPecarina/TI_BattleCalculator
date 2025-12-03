package com.dino.tibattlecalculator.ti

import android.content.Context
import com.dino.tibattlecalculator.domain.ShipType
import com.google.gson.Gson

object UnitStatsRepository {

    private var root: TiRootStats? = null

    fun ensureLoaded(context: Context) {
        if (root != null) return

        val jsonText = context.assets.open("ti_units.json")
            .bufferedReader()
            .use { it.readText() }

        val gson = Gson()
        root = gson.fromJson(jsonText, TiRootStats::class.java)
    }

    fun getEffectiveStats(
        unitKey: String,
        factionId: String? = null,
        upgradeId: String? = null
    ): EffectiveUnitStats {
        val r = root ?: error("UnitStatsRepository not loaded")

        val base = r.units[unitKey]
            ?: error("Unknown unit key: $unitKey")

        val upgrade = upgradeId?.let { r.unitUpgrades[it] }
        val faction = factionId?.let { r.factions[it] }
        val override = faction?.unitOverrides?.get(unitKey)

        val mergedDisplayName =
            override?.displayName ?: upgrade?.displayName ?: base.displayName

        val mergedSpaceCombat =
            override?.spaceCombat ?: upgrade?.spaceCombat ?: base.spaceCombat
            ?: error("No space combat stats for $unitKey")

        val mergedAbilities = mutableListOf<TiAbility>()
        mergedAbilities += base.abilities
        upgrade?.abilities?.let { mergedAbilities += it }
        override?.abilities?.let { mergedAbilities += it }

        val hasSustain = mergedAbilities.any { it.key == "SUSTAIN_DAMAGE" }
        val afb = mergedAbilities.firstOrNull { it.key == "ANTI_FIGHTER_BARRAGE" }
        val afbDice = afb?.dice ?: 0
        val afbHitOn = afb?.hitOn ?: 0

        val costResources = base.cost?.resources ?: 0

        return EffectiveUnitStats(
            id = upgradeId ?: unitKey,
            shipType = ShipType.valueOf(base.shipType),
            displayName = mergedDisplayName,
            spaceDice = mergedSpaceCombat.dice,
            spaceHitOn = mergedSpaceCombat.hitOn,
            hasSustainDamage = hasSustain,
            antiFighterDice = afbDice,
            antiFighterHitOn = afbHitOn,
            costResources = costResources
        )
    }

    fun getEffectiveStatsForBattle(
        shipType: ShipType,
        factionId: String? = null,
        isUpgraded: Boolean = false
    ): EffectiveUnitStats {
        val unitKey = unitKeyForShipType(shipType)
        val upgradeId = if (isUpgraded) upgradeIdForShipType(shipType) else null
        return getEffectiveStats(unitKey, factionId, upgradeId)
    }

    fun getUnitResourceCost(
        shipType: ShipType,
        factionId: String? = null,
        isUpgraded: Boolean = false
    ): Int {
        val stats = getEffectiveStatsForBattle(shipType, factionId, isUpgraded)
        return stats.costResources
    }

    fun getFactionCombatModifier(factionId: String?): Int {
        val r = root ?: error("UnitStatsRepository not loaded")
        if (factionId == null) return 0
        val f = r.factions[factionId] ?: return 0
        return f.combatModifier ?: 0
    }

    private fun unitKeyForShipType(shipType: ShipType): String =
        when (shipType) {
            ShipType.Fighter -> "fighter"
            ShipType.Destroyer -> "destroyer"
            ShipType.Cruiser -> "cruiser"
            ShipType.Dreadnought -> "dreadnought"
            ShipType.Carrier -> "carrier"
            ShipType.WarSun -> "war_sun"
            ShipType.Flagship -> "flagship_generic"
            ShipType.Unknown -> error("Unknown ship type cannot be mapped to a unit key")
        }

    private fun upgradeIdForShipType(shipType: ShipType): String? =
        when (shipType) {
            ShipType.Fighter -> "fighter_2"
            ShipType.Destroyer -> "destroyer_2"
            ShipType.Cruiser -> "cruiser_2"
            ShipType.Carrier -> "carrier_2"
            ShipType.Dreadnought -> "dreadnought_2"
            ShipType.WarSun -> null
            ShipType.Flagship -> null
            ShipType.Unknown -> null
        }
}
