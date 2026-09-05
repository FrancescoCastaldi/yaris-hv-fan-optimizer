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
        private const val KEY_TRANSPORT = "saved_transport_type"
        private const val KEY_FAN_SPEED = "forced_fan_speed"
        private const val KEY_BEST_0_50 = "dragy_best_0_50"
        private const val KEY_BEST_0_100 = "dragy_best_0_100"
        private const val KEY_LAST_SPRINT_TS = "dragy_last_sprint_ts"
        private const val KEY_LAST_0_100 = "dragy_last_0_100"
        private const val KEY_LAST_SPRINT_BATT_TEMP = "dragy_last_batt_temp"
        private const val KEY_LAST_SPRINT_ECT = "dragy_last_ect"
        private const val KEY_AUTO_COOLING_ENABLED = "auto_cooling_enabled"
        private const val KEY_AUTO_COOLING_TRIGGER = "auto_cooling_trigger_temp"
        private const val KEY_AUTO_COOLING_HYSTERESIS = "auto_cooling_hysteresis"
        private const val KEY_AUTO_COOLING_SPEED = "auto_cooling_target_speed"
    }

    var savedTransportType: String
        get() = prefs.getString(KEY_TRANSPORT, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_TRANSPORT, value).apply()

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

    var best0to50TimeSec: Float?
        get() = if (prefs.contains(KEY_BEST_0_50)) prefs.getFloat(KEY_BEST_0_50, 0f) else null
        set(value) = if (value != null) prefs.edit().putFloat(KEY_BEST_0_50, value).apply() else prefs.edit().remove(KEY_BEST_0_50).apply()

    var best0to100TimeSec: Float?
        get() = if (prefs.contains(KEY_BEST_0_100)) prefs.getFloat(KEY_BEST_0_100, 0f) else null
        set(value) = if (value != null) prefs.edit().putFloat(KEY_BEST_0_100, value).apply() else prefs.edit().remove(KEY_BEST_0_100).apply()

    var lastSprintTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SPRINT_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SPRINT_TS, value).apply()

    var lastSprint0to100Sec: Float?
        get() = if (prefs.contains(KEY_LAST_0_100)) prefs.getFloat(KEY_LAST_0_100, 0f) else null
        set(value) = if (value != null) prefs.edit().putFloat(KEY_LAST_0_100, value).apply() else prefs.edit().remove(KEY_LAST_0_100).apply()

    var lastSprintBatteryTemp: Float
        get() = prefs.getFloat(KEY_LAST_SPRINT_BATT_TEMP, 0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_SPRINT_BATT_TEMP, value).apply()

    var lastSprintCoolantTemp: Float
        get() = prefs.getFloat(KEY_LAST_SPRINT_ECT, 0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_SPRINT_ECT, value).apply()

    var isAutoCoolingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COOLING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COOLING_ENABLED, value).apply()

    var autoCoolingTriggerTemp: Float
        get() = prefs.getFloat(KEY_AUTO_COOLING_TRIGGER, 34.0f)
        set(value) = prefs.edit().putFloat(KEY_AUTO_COOLING_TRIGGER, value).apply()

    var autoCoolingHysteresis: Float
        get() = prefs.getFloat(KEY_AUTO_COOLING_HYSTERESIS, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_AUTO_COOLING_HYSTERESIS, value).apply()

    var autoCoolingTargetSpeed: Int
        get() = prefs.getInt(KEY_AUTO_COOLING_SPEED, 6)
        set(value) = prefs.edit().putInt(KEY_AUTO_COOLING_SPEED, value).apply()

    fun clearDevice() {
        prefs.edit().remove(KEY_SAVED_MAC).remove(KEY_SAVED_NAME).remove(KEY_TRANSPORT).apply()
    }
}
