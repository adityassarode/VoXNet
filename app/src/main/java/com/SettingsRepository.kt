package com.voxnet.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voxnet_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSettings(): VoXNetSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) gson.fromJson(json, VoXNetSettings::class.java) else VoXNetSettings()
    }

    fun saveSettings(settings: VoXNetSettings) {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun testConnection(settings: VoXNetSettings, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(settings.host, settings.port), 5000)
                socket.close()
                callback(true, "Connection successful")
            } catch (e: Exception) {
                callback(false, "Connection failed: ${e.message}")
            }
        }.start()
    }
}