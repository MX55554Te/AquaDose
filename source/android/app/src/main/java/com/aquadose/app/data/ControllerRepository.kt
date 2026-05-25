package com.aquadose.app.data

import com.aquadose.api.AquaDoseApiClient
import com.aquadose.api.AquaDoseResult
import com.aquadose.api.AquaDoseStatus
import com.aquadose.api.PumpConfigUpdate

class ControllerRepository(
    private val settingsRepository: ControllerSettingsRepository,
    private val apiClientFactory: AquaDoseApiClientFactory,
) {
    fun getSavedControllerIp(): String =
        settingsRepository.getControllerIp()

    fun saveControllerIp(ipAddress: String) {
        settingsRepository.saveControllerIp(ipAddress)
    }

    fun clearSavedControllerIp(): String {
        settingsRepository.clearControllerIp()
        return settingsRepository.getControllerIp()
    }

    fun getSetupControllerIp(): String =
        ControllerSettingsRepository.SETUP_AP_CONTROLLER_IP

    fun saveSetupControllerIp(): String {
        settingsRepository.saveControllerIp(ControllerSettingsRepository.SETUP_AP_CONTROLLER_IP)
        return ControllerSettingsRepository.SETUP_AP_CONTROLLER_IP
    }

    fun createApiClient(): AquaDoseApiClient =
        apiClientFactory.create(settingsRepository.getControllerIp())

    suspend fun checkConnection(controllerAddress: String): AquaDoseResult<AquaDoseStatus> {
        val normalizedAddress = normalizeControllerAddress(controllerAddress)
        val result = apiClientFactory.create(normalizedAddress).getStatus()
        if (result is AquaDoseResult.Success) {
            settingsRepository.saveControllerIp(normalizedAddress)
        }
        return result
    }

    suspend fun refreshStatus(controllerAddress: String): AquaDoseResult<AquaDoseStatus> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).getStatus()

    suspend fun emergencyStop(controllerAddress: String): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).emergencyStop()

    suspend fun manualDose(controllerAddress: String, pumpIndex: Int, ml: Double): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).manualDose(pumpIndex, ml)

    suspend fun primeStart(controllerAddress: String, pumpIndex: Int): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).primeStart(pumpIndex)

    suspend fun primeStop(controllerAddress: String): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).primeStop()

    suspend fun calibrationRun(controllerAddress: String, pumpIndex: Int): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).calibrationRun(pumpIndex)

    suspend fun calibrationSave(controllerAddress: String, pumpIndex: Int, grams: Double): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).calibrationSave(pumpIndex, grams)

    suspend fun savePumpConfig(controllerAddress: String, update: PumpConfigUpdate): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).savePumpConfig(update)

    suspend fun saveTimezone(controllerAddress: String, timezone: String): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).saveTimezone(timezone)

    suspend fun resetWifi(controllerAddress: String): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).resetWifi()

    suspend fun factoryReset(controllerAddress: String): AquaDoseResult<String> =
        apiClientFactory.create(normalizeControllerAddress(controllerAddress)).factoryReset()

    private fun normalizeControllerAddress(controllerAddress: String): String {
        val trimmed = controllerAddress.trim().trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return "http://$trimmed"
    }
}
