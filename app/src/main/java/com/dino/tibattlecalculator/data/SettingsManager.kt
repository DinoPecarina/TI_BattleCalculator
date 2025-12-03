package com.dino.tibattlecalculator.data

import android.content.Context
import org.json.JSONObject
import java.io.File

object SettingsManager {
    private const val FILE_NAME = "settings.json"

    fun load(context: Context): AppSettings {
        val file = File(context.filesDir, FILE_NAME)

        if (!file.exists()) return AppSettings()

        return try {
            val text = file.readText()
            val json = JSONObject(text)
            AppSettings(
                resourceUsage = json.optString("resourceUsage", "medium")
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save(context: Context, settings: AppSettings) {
        val file = File(context.filesDir, FILE_NAME)

        val json = JSONObject().apply {
            put("resourceUsage", settings.resourceUsage)
        }

        file.writeText(json.toString())
    }
}
