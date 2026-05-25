package com.aquadose.app.data

import android.content.Context

class ControllerSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getControllerIp(): String =
        preferences.getString(KEY_CONTROLLER_IP, DEFAULT_CONTROLLER_IP) ?: DEFAULT_CONTROLLER_IP

    fun saveControllerIp(ipAddress: String) {
        preferences.edit()
            .putString(KEY_CONTROLLER_IP, ipAddress.trim())
            .apply()
    }

    fun clearControllerIp() {
        preferences.edit()
            .remove(KEY_CONTROLLER_IP)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "aquadose_controller_settings"
        private const val KEY_CONTROLLER_IP = "controller_ip"
        const val DEFAULT_CONTROLLER_IP = "http://192.168.0.129"
        const val SETUP_AP_CONTROLLER_IP = "http://192.168.4.1"
    }
}
