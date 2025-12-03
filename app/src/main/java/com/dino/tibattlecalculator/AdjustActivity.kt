package com.dino.tibattlecalculator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.dino.tibattlecalculator.data.SettingsManager
import com.dino.tibattlecalculator.domain.ShipType
import com.dino.tibattlecalculator.sim.BattleConfig
import com.dino.tibattlecalculator.sim.BattleResult
import com.dino.tibattlecalculator.sim.BattleSimulator
import com.dino.tibattlecalculator.sim.FleetConfig
import com.dino.tibattlecalculator.ti.UnitStatsRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class AdjustActivity : AppCompatActivity() {

    data class ShipCounterViews(
        val root: View,
        val nameText: TextView,
        val countText: TextView,
        val minusButton: MaterialButton,
        val plusButton: MaterialButton,
        val upgradeCheck: CheckBox
    )

    private val shipTypes = listOf(
        ShipType.Fighter,
        ShipType.Destroyer,
        ShipType.Cruiser,
        ShipType.Dreadnought,
        ShipType.Carrier,
        ShipType.WarSun,
        ShipType.Flagship
    )

    private val player1ShipCounters = mutableMapOf<ShipType, ShipCounterViews>()
    private val player2ShipCounters = mutableMapOf<ShipType, ShipCounterViews>()
    private val player1UpgradedShips = mutableSetOf<ShipType>()
    private val player2UpgradedShips = mutableSetOf<ShipType>()
    private lateinit var drawerLayout: DrawerLayout
    private var detectionSampleBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adjust)

        supportActionBar?.title = "Adjust fleets"

        drawerLayout = findViewById(R.id.adjustDrawerLayout)
        val homeButton = findViewById<ImageButton>(R.id.headerHomeButton)
        val settingsButton = findViewById<ImageButton>(R.id.headerSettingsButton)

        val resourceSpinner = findViewById<AppCompatSpinner>(R.id.spinnerResourceUsage)

        val usageOptions = resources.getStringArray(R.array.resource_usage_options)
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            usageOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        resourceSpinner.adapter = spinnerAdapter

        var currentSettings = SettingsManager.load(this)
        val currentIndex = usageOptions.indexOfFirst {
            it.equals(currentSettings.resourceUsage, ignoreCase = true)
        }
        if (currentIndex >= 0) {
            resourceSpinner.setSelection(currentIndex)
        }

        resourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = usageOptions[position]
                val updated = currentSettings.copy(resourceUsage = selected.lowercase())
                currentSettings = updated
                SettingsManager.save(this@AdjustActivity, updated)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        homeButton.setOnClickListener {
            finish()
        }

        settingsButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        val detectionCard = findViewById<View>(R.id.detectionSampleCard)
        val detectionImage = findViewById<ImageView>(R.id.detectionSampleImage)
        val detectionPerfText = findViewById<TextView>(R.id.detectionPerfText)

        val player1ShipsContainer = findViewById<LinearLayout>(R.id.player1ShipsContainer)
        val player2ShipsContainer = findViewById<LinearLayout>(R.id.player2ShipsContainer)
        val player1FactionDropdown =
            findViewById<MaterialAutoCompleteTextView>(R.id.player1FactionDropdown)
        val player2FactionDropdown =
            findViewById<MaterialAutoCompleteTextView>(R.id.player2FactionDropdown)
        val calculateButton = findViewById<MaterialButton>(R.id.calculateButton)
        val player1Title = findViewById<TextView>(R.id.player1Title)
        val player2Title = findViewById<TextView>(R.id.player2Title)

        val p1ColorName = intent.getStringExtra("PLAYER1_COLOR_NAME") ?: "Unknown"
        val p2ColorName = intent.getStringExtra("PLAYER2_COLOR_NAME") ?: "Unknown"
        val p1CountsExtra = intent.getIntArrayExtra("PLAYER1_COUNTS")
        val p2CountsExtra = intent.getIntArrayExtra("PLAYER2_COUNTS")

        val croppedBytes = intent.getByteArrayExtra("CROPPED_SAMPLE")
        val detectLatencyMs = intent.getDoubleExtra("DETECT_LATENCY_MS", -1.0)
        val modelLatencyMs = intent.getDoubleExtra("MODEL_LATENCY_MS", -1.0)
        val fpsExtra = intent.getDoubleExtra("DETECT_FPS", -1.0)

        if (croppedBytes != null && croppedBytes.isNotEmpty()) {
            val bmp = BitmapFactory.decodeByteArray(croppedBytes, 0, croppedBytes.size)
            detectionSampleBitmap = bmp
            detectionImage.setImageBitmap(bmp)
            detectionCard.visibility = View.VISIBLE

            if (detectLatencyMs > 0.0) {
                val modelStr = if (modelLatencyMs > 0.0) {
                    modelLatencyMs.toString()
                } else {
                    "--"
                }
                val detectStr = detectLatencyMs.toString()
                val fps = if (detectLatencyMs > 0.0) {
                    1000.0 / detectLatencyMs
                } else {
                    fpsExtra
                }

                detectionPerfText.text = buildString {
                    append("modelMs_display=")
                    append(modelStr)
                    append(", detectTotalMs_display=")
                    append(detectStr)

                    if (fps > 0.0) {
                        append("\n~")
                        append(fps.format2())
                        append(" FPS")
                    }
                }
            } else {
                detectionPerfText.text = ""
            }
            // === CHANGED BLOCK END ===

            detectionCard.setOnClickListener {
                detectionSampleBitmap?.let { showDetectionSampleDialog(it) }
            }
        } else {
            detectionCard.visibility = View.GONE
        }

        player1Title.text = "Player 1 ($p1ColorName)"
        player2Title.text = "Player 2 ($p2ColorName)"

        setupFactionDropdown(player1FactionDropdown)
        setupFactionDropdown(player2FactionDropdown)

        val initialPlayer1Counts = mutableMapOf<ShipType, Int>()
        val initialPlayer2Counts = mutableMapOf<ShipType, Int>()

        for (index in shipTypes.indices) {
            val type = shipTypes[index]
            val c1 = p1CountsExtra?.let { if (index < it.size) it[index] else 0 } ?: 0
            val c2 = p2CountsExtra?.let { if (index < it.size) it[index] else 0 } ?: 0
            initialPlayer1Counts[type] = c1
            initialPlayer2Counts[type] = c2
        }

        val inflater = LayoutInflater.from(this)

        for (ship in shipTypes) {
            val views1 = createShipRow(
                inflater,
                player1ShipsContainer,
                ship.displayName,
                initialPlayer1Counts[ship] ?: 0
            )
            player1ShipCounters[ship] = views1

            views1.upgradeCheck.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    player1UpgradedShips.add(ship)
                } else {
                    player1UpgradedShips.remove(ship)
                }
            }

            val views2 = createShipRow(
                inflater,
                player2ShipsContainer,
                ship.displayName,
                initialPlayer2Counts[ship] ?: 0
            )
            player2ShipCounters[ship] = views2

            views2.upgradeCheck.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    player2UpgradedShips.add(ship)
                } else {
                    player2UpgradedShips.remove(ship)
                }
            }
        }

        calculateButton.setOnClickListener {
            UnitStatsRepository.ensureLoaded(this)

            val p1FactionName = player1FactionDropdown.text?.toString().orEmpty()
            val p2FactionName = player2FactionDropdown.text?.toString().orEmpty()

            val p1Ships = shipTypes.associateWith { type ->
                player1ShipCounters[type]?.countText?.text?.toString()?.toIntOrNull() ?: 0
            }
            val p2Ships = shipTypes.associateWith { type ->
                player2ShipCounters[type]?.countText?.text?.toString()?.toIntOrNull() ?: 0
            }

            val p1FactionId = mapFactionNameToId(p1FactionName)
            val p2FactionId = mapFactionNameToId(p2FactionName)

            val totalP1 = p1Ships.values.sum()
            val totalP2 = p2Ships.values.sum()
            if (totalP1 == 0 || totalP2 == 0) {
                Toast.makeText(
                    this,
                    "Both players must have at least one ship to simulate.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val p1Upgraded = player1UpgradedShips.toSet()
            val p2Upgraded = player2UpgradedShips.toSet()

            val p1FactionModifier = UnitStatsRepository.getFactionCombatModifier(p1FactionId)
            val p2FactionModifier = UnitStatsRepository.getFactionCombatModifier(p2FactionId)

            val p1Fleet = FleetConfig(
                name = "Player 1 ($p1ColorName)",
                factionId = p1FactionId,
                ships = p1Ships,
                upgradedShips = p1Upgraded,
                factionCombatModifier = p1FactionModifier
            )

            val p2Fleet = FleetConfig(
                name = "Player 2 ($p2ColorName)",
                factionId = p2FactionId,
                ships = p2Ships,
                upgradedShips = p2Upgraded,
                factionCombatModifier = p2FactionModifier
            )

            val settings = SettingsManager.load(this)
            val simulations = simulationsForUsage(settings.resourceUsage)

            val battleConfig = BattleConfig(
                player1 = p1Fleet,
                player2 = p2Fleet,
                simulations = simulations
            )

            Thread {
                val simulator = BattleSimulator()
                val result = simulator.simulate(battleConfig, UnitStatsRepository)

                runOnUiThread {
                    showBattleResultDialog(
                        p1Fleet.name,
                        p2Fleet.name,
                        p1FactionName,
                        p2FactionName,
                        result,
                        simulations
                    )
                }
            }.start()
        }
    }


    private fun setupFactionDropdown(dropdown: MaterialAutoCompleteTextView) {
        val factions = resources.getStringArray(R.array.ti_factions)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            factions
        )
        dropdown.setAdapter(adapter)

        dropdown.isFocusable = true
        dropdown.isFocusableInTouchMode = true
        dropdown.isClickable = true

        dropdown.setOnClickListener {
            dropdown.showDropDown()
        }

        dropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dropdown.showDropDown()
            }
        }
    }

    private fun createShipRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        shipName: String,
        initialCount: Int
    ): ShipCounterViews {
        val row = inflater.inflate(R.layout.item_ship_counter, parent, false)

        val nameText = row.findViewById<TextView>(R.id.shipNameText)
        val countText = row.findViewById<TextView>(R.id.countText)
        val minusButton = row.findViewById<MaterialButton>(R.id.minusButton)
        val plusButton = row.findViewById<MaterialButton>(R.id.plusButton)
        val upgradeCheck = row.findViewById<CheckBox>(R.id.upgradeCheck)

        nameText.text = shipName
        countText.text = initialCount.toString()

        minusButton.setOnClickListener {
            val current = countText.text.toString().toIntOrNull() ?: 0
            val newVal = (current - 1).coerceAtLeast(0)
            countText.text = newVal.toString()
        }

        plusButton.setOnClickListener {
            val current = countText.text.toString().toIntOrNull() ?: 0
            val newVal = current + 1
            countText.text = newVal.toString()
        }

        parent.addView(row)

        return ShipCounterViews(
            root = row,
            nameText = nameText,
            countText = countText,
            minusButton = minusButton,
            plusButton = plusButton,
            upgradeCheck = upgradeCheck
        )
    }

    private fun mapFactionNameToId(name: String): String? {
        return when (name.trim()) {

            "Arborec" -> "arborec"
            "Barony of Letnev" -> "barony_of_letnev"
            "Clan of Saar" -> "clan_of_saar"
            "Emirates of Hacan" -> "emirates_of_hacan"
            "Federation of Sol" -> "federation_of_sol"
            "Ghosts of Creuss" -> "ghosts_of_creuss"
            "L1Z1X Mindnet" -> "l1z1x_mindnet"
            "Mentak Coalition" -> "mentak_coalition"
            "Naalu Collective" -> "naalu_collective"
            "Nekro Virus" -> "nekro_virus"
            "Sardakk N'orr" -> "sardakk_norr"
            "Universities of Jol-Nar" -> "universities_of_jol_nar"
            "Winnu" -> "winnu"
            "Xxcha Kingdom" -> "xxcha_kingdom"
            "Yin Brotherhood" -> "yin_brotherhood"
            "Yssaril Tribes" -> "yssaril_tribes"

            "" -> null
            else -> null
        }
    }


    private fun simulationsForUsage(usage: String): Int =
        when (usage.lowercase()) {
            "low" -> 500
            "medium" -> 3000
            "high" -> 10000
            else -> 3000
        }

    private fun showBattleResultDialog(
        p1Name: String,
        p2Name: String,
        p1FactionName: String,
        p2FactionName: String,
        result: BattleResult,
        simulations: Int
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_battle_result, null)

        val drawText = dialogView.findViewById<TextView>(R.id.drawText)
        val simulationsText = dialogView.findViewById<TextView>(R.id.simulationsText)

        val p1NameText = dialogView.findViewById<TextView>(R.id.p1NameText)
        val p1FactionText = dialogView.findViewById<TextView>(R.id.p1FactionText)
        val p1WinText = dialogView.findViewById<TextView>(R.id.p1WinText)
        val p1LossesText = dialogView.findViewById<TextView>(R.id.p1LossesText)
        val p1ResourceLossText = dialogView.findViewById<TextView>(R.id.p1ResourceLossText)

        val p2NameText = dialogView.findViewById<TextView>(R.id.p2NameText)
        val p2FactionText = dialogView.findViewById<TextView>(R.id.p2FactionText)
        val p2WinText = dialogView.findViewById<TextView>(R.id.p2WinText)
        val p2LossesText = dialogView.findViewById<TextView>(R.id.p2LossesText)
        val p2ResourceLossText = dialogView.findViewById<TextView>(R.id.p2ResourceLossText)

        p1NameText.text = p1Name
        p2NameText.text = p2Name

        p1FactionText.text =
            if (p1FactionName.isBlank()) "No faction" else p1FactionName
        p2FactionText.text =
            if (p2FactionName.isBlank()) "No faction" else p2FactionName

        p1WinText.text = "Win: ${(result.p1WinRate * 100).format2()}%"
        p2WinText.text = "Win: ${(result.p2WinRate * 100).format2()}%"

        drawText.text = "Draw: ${(result.drawRate * 100).format2()}%"
        simulationsText.text = "Simulations: $simulations"

        p1ResourceLossText.text =
            "Avg resource loss: ${result.avgResourceLossP1.format2()}"
        p2ResourceLossText.text =
            "Avg resource loss: ${result.avgResourceLossP2.format2()}"

        val lossesP1 = buildString {
            result.avgLossesP1.forEach { (type, avg) ->
                appendLine("• ${type.displayName}: ${avg.format3()}")
            }
        }
        val lossesP2 = buildString {
            result.avgLossesP2.forEach { (type, avg) ->
                appendLine("• ${type.displayName}: ${avg.format3()}")
            }
        }

        p1LossesText.text = lossesP1.trimEnd()
        p2LossesText.text = lossesP2.trimEnd()

        MaterialAlertDialogBuilder(this)
            .setTitle("Battle Simulation Result")
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }


    private fun showDetectionSampleDialog(bitmap: Bitmap) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_detection_sample, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogDetectionImage)
        imageView.setImageBitmap(bitmap)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.detection_sample))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun Double.format2(): String = String.format("%.2f", this)
    private fun Double.format3(): String = String.format("%.3f", this)
}
