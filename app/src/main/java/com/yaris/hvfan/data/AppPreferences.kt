package com.yaris.hvfan.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("yaris_hv_fan_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVED_MAC = "saved_ble_mac"
        private const val KEY_SAVED_NAME = "saved_ble_name"
        private const val KEY_TARGET_TEMP = "target_temp_threshold"
        private const val KEY_AUTO_START = "auto_start_enabled"
        private const val KEY_FAN_SPEED = "forced_fan_speed"
    }

    var savedMacAddress: String?
        get() = prefs.getString(KEY_SAVED_MAC, null)
        set(value) = prefs.edit().putString(KEY_SAVED_MAC, value).apply()

    var savedDeviceName: String?
        get() = prefs.getString(KEY_SAVED_NAME, null)
        set(value) = prefs.edit().putString(KEY_SAVED_NAME, value).apply()

    var targetTempThreshold: Int
        get() = prefs.getInt(KEY_TARGET_TEMP, 20) // Default 20°C (Dr. Prius style threshold)
        set(value) = prefs.edit().putInt(KEY_TARGET_TEMP, value).apply()

    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var forcedFanSpeed: Int
        get() = prefs.getInt(KEY_FAN_SPEED, 6) // Max level 6
        set(value) = prefs.edit().putInt(KEY_FAN_SPEED, value).apply()

    fun clearDevice() {
        prefs.edit().remove(KEY_SAVED_MAC).remove(KEY_SAVED_NAME).apply()
    }
}
