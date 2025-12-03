package com.dino.tibattlecalculator.ti
import com.dino.tibattlecalculator.domain.ShipType

data class TiRootStats(
    val edition: String,
    val version: Int,
    val units: Map<String, TiUnitDef>,
    val unitUpgrades: Map<String, TiUnitUpgrade> = emptyMap(),
    val factions: Map<String, TiFactionDef> = emptyMap()
)

data class TiUnitDef(
    val displayName: String,
    val shipType: String, // will map to ShipType
    val cost: TiCost? = null,
    val spaceCombat: TiSpaceCombat? = null,
    val move: Int = 0,
    val capacity: Int = 0,
    val abilities: List<TiAbility> = emptyList()
)

data class TiCost(
    val resources: Int,
    val unitsProduced: Int
)

data class TiSpaceCombat(
    val dice: Int,
    val hitOn: Int
)

data class TiAbility(
    val key: String,
    val dice: Int? = null,
    val hitOn: Int? = null
)

data class TiUnitUpgrade(
    val baseId: String,
    val displayName: String? = null,
    val spaceCombat: TiSpaceCombat? = null,
    val move: Int? = null,
    val capacity: Int? = null,
    val abilities: List<TiAbility>? = null
)

data class TiFactionDef(
    val displayName: String,
    val unitOverrides: Map<String, TiUnitOverride> = emptyMap(),
    val flagship: TiUnitDef? = null,
    val combatModifier: Int? = null
)

data class TiUnitOverride(
    val displayName: String? = null,
    val spaceCombat: TiSpaceCombat? = null,
    val move: Int? = null,
    val capacity: Int? = null,
    val abilities: List<TiAbility>? = null
)
