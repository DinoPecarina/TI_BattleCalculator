package com.dino.tibattlecalculator

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dino.tibattlecalculator.data.SettingsManager
import com.dino.tibattlecalculator.domain.ShipType
import com.dino.tibattlecalculator.sim.BattleConfig
import com.dino.tibattlecalculator.sim.BattleSimulator
import com.dino.tibattlecalculator.sim.FleetConfig
import com.dino.tibattlecalculator.ti.UnitStatsRepository

class BattleTestActivity : AppCompatActivity() {

    private lateinit var txtSummary: TextView
    private lateinit var txtLossesP1: TextView
    private lateinit var txtLossesP2: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battle_test)

        txtSummary = findViewById(R.id.txtSummary)
        txtLossesP1 = findViewById(R.id.txtLossesP1)
        txtLossesP2 = findViewById(R.id.txtLossesP2)

        supportActionBar?.title = "Battle Test"

        val settings = SettingsManager.load(this)
        val sims = simulationsForUsage(settings.resourceUsage)

        UnitStatsRepository.ensureLoaded(this)

        val p1Fleet = FleetConfig(
            name = "Player 1",
            factionId = null,
            ships = mapOf(
                ShipType.Dreadnought to 2,
                ShipType.Cruiser to 3,
                ShipType.Fighter to 6
            )
        )

        val p2Fleet = FleetConfig(
            name = "Player 2",
            factionId = null,
            ships = mapOf(
                ShipType.WarSun to 1,
                ShipType.Destroyer to 2,
                ShipType.Fighter to 8
            )
        )

        val battleConfig = BattleConfig(
            player1 = p1Fleet,
            player2 = p2Fleet,
            simulations = sims
        )


        Thread {
            val simulator = BattleSimulator()
            val result = simulator.simulate(battleConfig, UnitStatsRepository)

            runOnUiThread {
                showResult(p1Fleet.name, p2Fleet.name, result)
            }
        }.start()
    }

    private fun simulationsForUsage(usage: String): Int =
        when (usage.lowercase()) {
            "low" -> 500
            "medium" -> 3000
            "high" -> 10000
            else -> 3000
        }

    private fun showResult(
        p1Name: String,
        p2Name: String,
        result: com.dino.tibattlecalculator.sim.BattleResult
    ) {
        val summaryText = buildString {
            appendLine("Simulations: ${result.p1WinRate + result.p2WinRate + result.drawRate}")
            appendLine("$p1Name win: ${(result.p1WinRate * 100).format1()}%")
            appendLine("$p2Name win: ${(result.p2WinRate * 100).format1()}%")
            appendLine("Draw: ${(result.drawRate * 100).format1()}%")
        }

        val lossesP1Text = buildString {
            appendLine("$p1Name average losses:")
            result.avgLossesP1.forEach { (type, avg) ->
                appendLine(" - ${type.name}: ${avg.format1()}")
            }
        }

        val lossesP2Text = buildString {
            appendLine("$p2Name average losses:")
            result.avgLossesP2.forEach { (type, avg) ->
                appendLine(" - ${type.name}: ${avg.format1()}")
            }
        }

        txtSummary.text = summaryText
        txtLossesP1.text = lossesP1Text
        txtLossesP2.text = lossesP2Text
    }

    private fun Double.format1(): String = String.format("%.1f", this)
}
