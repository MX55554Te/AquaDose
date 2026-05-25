package com.aquadose.app.ui

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aquadose.api.AquaDosePump
import com.aquadose.api.AquaDoseResult
import com.aquadose.api.AquaDoseSchedule
import com.aquadose.api.AquaDoseStatus
import com.aquadose.api.AquaDoseValidation
import com.aquadose.api.PumpConfigUpdate
import com.aquadose.api.ScheduleConfigUpdate
import com.aquadose.app.BuildConfig
import com.aquadose.app.R
import com.aquadose.app.data.ControllerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val MAX_PUMP_NAME_LENGTH = 31
private const val MAX_DIRECT_CALIBRATION_RATE_ML_PER_SEC = 100.0

data class MainUiState(
    val controllerAddress: String = "",
    val currentScreen: AppScreen = AppScreen.Dashboard,
    val selectedPumpId: Int? = null,
    val isCheckingConnection: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NotConnected,
    val statusSummary: String = "",
    val rawErrorDetails: String = "",
    val controllerStatus: AquaDoseStatus? = null,
    val isRefreshingStatus: Boolean = false,
    val lastStatusMessage: String = "",
    val emergencyStopInProgress: Boolean = false,
    val emergencyStopMessage: String = "",
    val manualDoseMl: String = "",
    val calibrationGrams: String = "",
    val pumpNameDraft: String = "",
    val pumpCalibrationRateDraft: String = "",
    val pumpSettingsSaveInProgress: Boolean = false,
    val pumpSettingsMessage: String = "",
    val pumpActionInProgress: Boolean = false,
    val pumpScheduleToggleInProgress: Int? = null,
    val pumpActionMessage: String = "",
    val scheduleDrafts: List<ScheduleSlotDraft> = emptyList(),
    val scheduleSaveInProgress: Boolean = false,
    val scheduleMessage: String = "",
    val settingsControllerAddress: String = "",
    val timezoneInput: String = "",
    val settingsActionInProgress: Boolean = false,
    val settingsMessage: String = "",
)

data class ScheduleSlotDraft(
    val enabled: Boolean,
    val hour: String,
    val minute: String,
    val ml: String,
    val savedEnabled: Boolean = enabled,
    val savedHour: String = hour,
    val savedMinute: String = minute,
    val savedMl: String = ml,
)

enum class AppScreen {
    Dashboard,
    Pumps,
    PumpDetail,
    ScheduleEditor,
    Logs,
    Settings,
}

enum class ConnectionStatus {
    NotConnected,
    Connected,
    Disconnected,
}

class MainViewModel(
    private val controllerRepository: ControllerRepository,
    appContext: Context,
) : ViewModel() {
    private var pollingJob: Job? = null
    private val resources = appContext.applicationContext.resources

    private val mutableUiState = MutableStateFlow(
        MainUiState(
            controllerAddress = controllerRepository.getSavedControllerIp(),
            lastStatusMessage = text(R.string.vm_status_waiting),
        ),
    )
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    private fun text(@StringRes id: Int, vararg formatArgs: Any): String =
        resources.getString(id, *formatArgs)

    fun onControllerAddressChanged(value: String) {
        mutableUiState.update {
            it.copy(
                controllerAddress = value,
                connectionStatus = ConnectionStatus.NotConnected,
                statusSummary = "",
                rawErrorDetails = "",
                controllerStatus = null,
                lastStatusMessage = text(R.string.vm_status_address_changed),
            )
        }
    }

    fun useSuggestedController() {
        onControllerAddressChanged("http://192.168.0.129")
    }

    fun useSetupAp() {
        onControllerAddressChanged(controllerRepository.getSetupControllerIp())
    }

    fun showDashboard() {
        mutableUiState.update {
            it.copy(currentScreen = AppScreen.Dashboard, selectedPumpId = null)
        }
    }

    fun showPumps() {
        mutableUiState.update {
            it.copy(currentScreen = AppScreen.Pumps, selectedPumpId = null)
        }
    }

    fun showPumpDetail(pumpId: Int) {
        val pump = uiState.value.controllerStatus?.pumps?.firstOrNull { it.id == pumpId }
        mutableUiState.update {
            it.copy(
                currentScreen = AppScreen.PumpDetail,
                selectedPumpId = pumpId,
                pumpNameDraft = pump?.name?.ifBlank { text(R.string.pump_title, pumpId + 1) }.orEmpty(),
                pumpCalibrationRateDraft = pump?.mlPerSec?.toRateDraft().orEmpty(),
                pumpSettingsMessage = "",
            )
        }
    }

    fun showLogs() {
        mutableUiState.update { it.copy(currentScreen = AppScreen.Logs, selectedPumpId = null) }
    }

    fun showSettings() {
        mutableUiState.update {
            it.copy(
                currentScreen = AppScreen.Settings,
                selectedPumpId = null,
                settingsControllerAddress = it.controllerAddress,
                timezoneInput = it.controllerStatus?.tz ?: it.timezoneInput,
                settingsMessage = "",
            )
        }
    }

    fun showScheduleEditor(pumpId: Int) {
        val pump = uiState.value.controllerStatus?.pumps?.firstOrNull { it.id == pumpId }
        mutableUiState.update {
            it.copy(
                currentScreen = AppScreen.ScheduleEditor,
                selectedPumpId = pumpId,
                scheduleDrafts = pump?.schedules?.map { schedule ->
                    schedule.toDraft()
                }.orEmpty(),
                scheduleMessage = "",
            )
        }
    }

    fun onManualDoseMlChanged(value: String) {
        mutableUiState.update { it.copy(manualDoseMl = value, pumpActionMessage = "") }
    }

    fun onCalibrationGramsChanged(value: String) {
        mutableUiState.update { it.copy(calibrationGrams = value, pumpActionMessage = "") }
    }

    fun onPumpNameDraftChanged(value: String) {
        mutableUiState.update { it.copy(pumpNameDraft = value, pumpSettingsMessage = "") }
    }

    fun onPumpCalibrationRateDraftChanged(value: String) {
        mutableUiState.update { it.copy(pumpCalibrationRateDraft = value, pumpSettingsMessage = "") }
    }

    fun onSettingsControllerAddressChanged(value: String) {
        mutableUiState.update { it.copy(settingsControllerAddress = value, settingsMessage = "") }
    }

    fun onTimezoneInputChanged(value: String) {
        mutableUiState.update { it.copy(timezoneInput = value, settingsMessage = "") }
    }

    fun saveControllerAddressFromSettings() {
        val address = uiState.value.settingsControllerAddress.trim()
        if (address.isBlank()) {
            mutableUiState.update { it.copy(settingsMessage = text(R.string.vm_settings_address_empty)) }
            return
        }
        controllerRepository.saveControllerIp(address)
        mutableUiState.update {
            it.copy(
                controllerAddress = address,
                settingsControllerAddress = address,
                connectionStatus = ConnectionStatus.NotConnected,
                controllerStatus = null,
                settingsMessage = text(R.string.vm_settings_address_saved),
            )
        }
    }

    fun clearSavedControllerAddress() {
        val defaultAddress = controllerRepository.clearSavedControllerIp()
        mutableUiState.update {
            it.copy(
                controllerAddress = defaultAddress,
                settingsControllerAddress = defaultAddress,
                connectionStatus = ConnectionStatus.NotConnected,
                controllerStatus = null,
                settingsMessage = text(R.string.vm_settings_address_cleared, defaultAddress),
            )
        }
    }

    fun onScheduleEnabledChanged(slotIndex: Int, enabled: Boolean) {
        updateScheduleDraft(slotIndex) { it.copy(enabled = enabled) }
    }

    fun onScheduleHourChanged(slotIndex: Int, value: String) {
        updateScheduleDraft(slotIndex) { it.copy(hour = value) }
    }

    fun onScheduleMinuteChanged(slotIndex: Int, value: String) {
        updateScheduleDraft(slotIndex) { it.copy(minute = value) }
    }

    fun onScheduleMlChanged(slotIndex: Int, value: String) {
        updateScheduleDraft(slotIndex) { it.copy(ml = value) }
    }

    private fun updateScheduleDraft(
        slotIndex: Int,
        transform: (ScheduleSlotDraft) -> ScheduleSlotDraft,
    ) {
        mutableUiState.update { state ->
            if (slotIndex !in state.scheduleDrafts.indices) return@update state
            state.copy(
                scheduleDrafts = state.scheduleDrafts.mapIndexed { index, draft ->
                    if (index == slotIndex) transform(draft) else draft
                },
                scheduleMessage = "",
            )
        }
    }

    fun checkConnection() {
        val address = uiState.value.controllerAddress.trim()
        if (address.isEmpty()) {
            mutableUiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    rawErrorDetails = text(R.string.vm_error_address_required_connect),
                )
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isCheckingConnection = true,
                    connectionStatus = ConnectionStatus.NotConnected,
                    statusSummary = "",
                    rawErrorDetails = "",
                )
            }

            when (val result = controllerRepository.checkConnection(address)) {
                is AquaDoseResult.Success -> {
                    val status = result.value
                    mutableUiState.update {
                        it.copy(
                            controllerAddress = controllerRepository.getSavedControllerIp(),
                            isCheckingConnection = false,
                            connectionStatus = ConnectionStatus.Connected,
                            statusSummary = text(R.string.vm_status_connected_summary, status.pumps.size, status.currentTime),
                            rawErrorDetails = "",
                            controllerStatus = status,
                            lastStatusMessage = text(R.string.vm_status_connected),
                        )
                    }
                    startStatusPolling()
                }

                is AquaDoseResult.HttpFailure -> {
                    mutableUiState.update {
                        it.copy(
                            isCheckingConnection = false,
                            connectionStatus = ConnectionStatus.Disconnected,
                            rawErrorDetails = text(R.string.vm_error_controller_response),
                            lastStatusMessage = text(R.string.vm_status_connection_failed),
                        )
                    }
                }

                is AquaDoseResult.NetworkFailure -> {
                    mutableUiState.update {
                        it.copy(
                            isCheckingConnection = false,
                            connectionStatus = ConnectionStatus.Disconnected,
                            rawErrorDetails = text(R.string.vm_error_controller_unreachable),
                            lastStatusMessage = text(R.string.vm_status_connection_failed),
                        )
                    }
                }

                is AquaDoseResult.UnexpectedFailure -> {
                    mutableUiState.update {
                        it.copy(
                            isCheckingConnection = false,
                            connectionStatus = ConnectionStatus.Disconnected,
                            rawErrorDetails = text(R.string.vm_error_controller_unreadable),
                            lastStatusMessage = text(R.string.vm_status_connection_failed),
                        )
                    }
                }

                is AquaDoseResult.ValidationFailure -> {
                    mutableUiState.update {
                        it.copy(
                            isCheckingConnection = false,
                            connectionStatus = ConnectionStatus.Disconnected,
                            rawErrorDetails = text(R.string.vm_error_controller_incomplete),
                            lastStatusMessage = text(R.string.vm_status_connection_failed),
                        )
                    }
                }
            }
        }
    }

    fun startStatusPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshStatus(markRefreshing = false)
                delay(3000)
            }
        }
    }

    fun stopStatusPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun manualRefresh() {
        viewModelScope.launch {
            refreshStatus(markRefreshing = true)
        }
    }

    fun emergencyStop() {
        val address = uiState.value.controllerAddress.trim()
        if (address.isEmpty()) {
            mutableUiState.update {
                it.copy(
                    emergencyStopMessage = text(R.string.vm_stop_address_required),
                )
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    emergencyStopInProgress = true,
                    emergencyStopMessage = text(R.string.vm_stop_sending),
                )
            }

            logPumpEndpoint("/stop", "Emergency Stop button confirmed by user")
            val stopMessage = when (val result = controllerRepository.emergencyStop(address)) {
                is AquaDoseResult.Success -> text(R.string.vm_stop_success)
                is AquaDoseResult.HttpFailure -> text(R.string.vm_stop_failed)
                is AquaDoseResult.NetworkFailure -> text(R.string.vm_stop_unreachable)
                is AquaDoseResult.UnexpectedFailure -> text(R.string.vm_stop_unconfirmed)
                is AquaDoseResult.ValidationFailure -> text(R.string.vm_stop_unconfirmed)
            }

            mutableUiState.update {
                it.copy(
                    emergencyStopInProgress = false,
                    emergencyStopMessage = stopMessage,
                )
            }
            refreshStatus(markRefreshing = false, syncScheduleDrafts = true)
        }
    }

    fun startManualDose() {
        val state = uiState.value
        val pumpIndex = state.selectedPumpId ?: return setPumpActionMessage(text(R.string.vm_manual_no_pump))
        val ml = state.manualDoseMl.toDoubleOrNull()
            ?: return setPumpActionMessage(text(R.string.vm_manual_invalid_value))
        if (AquaDoseValidation.validateMl(ml) != null) {
            setPumpActionMessage(text(R.string.vm_manual_invalid_range, doseMinLabel(), doseMaxLabel()))
            return
        }
        runPumpAction(
            busyMessage = text(R.string.vm_manual_starting),
            endpoint = "/manual?p=$pumpIndex&ml=$ml",
            reason = "Start manual dose for pump ${pumpIndex + 1}, $ml ml",
            successMessage = text(R.string.vm_manual_started),
        ) {
            controllerRepository.manualDose(state.controllerAddress, pumpIndex, ml)
        }
    }

    fun startPrime() {
        val state = uiState.value
        val pumpIndex = state.selectedPumpId ?: return setPumpActionMessage(text(R.string.vm_prime_no_pump))
        runPumpAction(
            busyMessage = text(R.string.vm_prime_starting),
            endpoint = "/primeStart?p=$pumpIndex",
            reason = "Start prime for pump ${pumpIndex + 1}",
            successMessage = text(R.string.vm_prime_started),
        ) {
            controllerRepository.primeStart(state.controllerAddress, pumpIndex)
        }
    }

    fun stopPrime() {
        val state = uiState.value
        runPumpAction(
            busyMessage = text(R.string.vm_prime_stopping),
            endpoint = "/primeStop",
            reason = "Stop prime or active pump from Pump Detail",
            successMessage = text(R.string.vm_prime_stop_sent),
        ) {
            controllerRepository.primeStop(state.controllerAddress)
        }
    }

    fun runCalibration() {
        val state = uiState.value
        val pumpIndex = state.selectedPumpId ?: return setPumpActionMessage(text(R.string.vm_calibration_no_pump_start))
        runPumpAction(
            busyMessage = text(R.string.vm_calibration_starting),
            endpoint = "/calRun?p=$pumpIndex",
            reason = "Run 60 second calibration for pump ${pumpIndex + 1}",
            successMessage = text(R.string.vm_calibration_started),
        ) {
            controllerRepository.calibrationRun(state.controllerAddress, pumpIndex)
        }
    }

    fun saveCalibration() {
        val state = uiState.value
        val pumpIndex = state.selectedPumpId ?: return setPumpActionMessage(text(R.string.vm_calibration_no_pump_save))
        val grams = state.calibrationGrams.toDoubleOrNull()
            ?: return setPumpActionMessage(text(R.string.vm_calibration_missing_grams))
        if (grams <= 0.0) {
            setPumpActionMessage(text(R.string.vm_calibration_invalid_grams))
            return
        }
        runPumpAction(
            busyMessage = text(R.string.vm_calibration_saving),
            endpoint = "/calSave?p=$pumpIndex&grams=$grams",
            reason = "Save calibration for pump ${pumpIndex + 1}, measured grams=$grams",
            successMessage = text(R.string.vm_calibration_saved),
        ) {
            controllerRepository.calibrationSave(state.controllerAddress, pumpIndex, grams)
        }
    }

    fun savePumpSettings() {
        val state = uiState.value
        val pump = state.controllerStatus?.pumps?.firstOrNull { it.id == state.selectedPumpId }
            ?: return setPumpSettingsMessage(text(R.string.vm_pump_settings_no_pump))
        if (state.pumpSettingsSaveInProgress) return
        if (state.controllerAddress.isBlank()) {
            setPumpSettingsMessage(text(R.string.vm_action_address_required))
            return
        }

        val name = state.pumpNameDraft.trim()
        if (name.isBlank()) {
            setPumpSettingsMessage(text(R.string.vm_pump_name_empty))
            return
        }
        if (name.length > MAX_PUMP_NAME_LENGTH) {
            setPumpSettingsMessage(text(R.string.vm_pump_name_too_long, MAX_PUMP_NAME_LENGTH))
            return
        }

        val rateText = state.pumpCalibrationRateDraft.trim()
        val rate = if (rateText.isBlank()) {
            0.0
        } else {
            rateText.toDoubleOrNull()
                ?: return setPumpSettingsMessage(text(R.string.vm_pump_rate_invalid))
        }
        if (!rate.isFinite() || rate < 0.0 || rate > MAX_DIRECT_CALIBRATION_RATE_ML_PER_SEC) {
            setPumpSettingsMessage(text(R.string.vm_pump_rate_invalid))
            return
        }

        val invalidScheduleSlots = pump.scheduleSlotsWithInvalidDoses()
        if (invalidScheduleSlots.isNotEmpty()) {
            setPumpSettingsMessage(
                text(
                    R.string.vm_pump_settings_schedule_dose_invalid,
                    invalidScheduleSlots.joinToString(),
                    doseMinLabel(),
                    doseMaxLabel(),
                ),
            )
            return
        }

        val update = PumpConfigUpdate.fromStatusPump(pump).copy(
            name = name,
            mlPerSec = rate,
        )

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    pumpSettingsSaveInProgress = true,
                    pumpSettingsMessage = text(R.string.vm_pump_settings_saving),
                )
            }
            logPumpEndpoint(
                endpoint = "/savePump?p=${pump.id}&name=...&enabled=...&rate=...&s0...s7...",
                reason = "Save pump ${pump.id + 1} name and calibration rate while preserving schedule permission and all schedule slots",
            )
            when (controllerRepository.savePumpConfig(state.controllerAddress, update)) {
                is AquaDoseResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            pumpSettingsSaveInProgress = false,
                            pumpSettingsMessage = text(R.string.vm_pump_settings_saved),
                        )
                    }
                    refreshStatus(markRefreshing = false, syncSelectedPumpDraft = true)
                }

                is AquaDoseResult.HttpFailure -> restorePumpSettingsSave(text(R.string.vm_pump_settings_failed))
                is AquaDoseResult.NetworkFailure -> restorePumpSettingsSave(text(R.string.vm_pump_settings_unreachable))
                is AquaDoseResult.UnexpectedFailure -> restorePumpSettingsSave(text(R.string.vm_pump_settings_unreadable))
                is AquaDoseResult.ValidationFailure -> restorePumpSettingsSave(text(R.string.vm_pump_settings_rejected))
            }
        }
    }

    fun saveSchedules() {
        val state = uiState.value
        val pump = state.controllerStatus?.pumps?.firstOrNull { it.id == state.selectedPumpId }
            ?: return setScheduleMessage(text(R.string.vm_schedule_no_pump))
        if (state.scheduleSaveInProgress) return

        val parsedSchedules = mutableListOf<ScheduleConfigUpdate>()
        val errors = mutableListOf<String>()
        state.scheduleDrafts.forEachIndexed { index, draft ->
            val hour = draft.hour.toIntOrNull()
            val minute = draft.minute.toIntOrNull()
            val ml = draft.ml.toDoubleOrNull()
            if (hour == null || hour !in 0..23) {
                errors += text(R.string.vm_schedule_error_hour, index + 1)
            }
            if (minute == null || minute !in 0..59) {
                errors += text(R.string.vm_schedule_error_minute, index + 1)
            }
            if (draft.enabled && (ml == null || AquaDoseValidation.validateMl(ml) != null)) {
                errors += text(R.string.vm_schedule_error_dose, index + 1, doseMinLabel(), doseMaxLabel())
            }
            if (hour != null && minute != null) {
                val savedMl = if (draft.enabled) {
                    ml ?: 0.0
                } else {
                    ml?.takeIf { it > 0.0 } ?: pump.schedules[index].ml
                }
                parsedSchedules += ScheduleConfigUpdate(
                    enabled = draft.enabled,
                    hour = hour,
                    minute = minute,
                    ml = savedMl,
                )
            }
        }
        if (errors.isNotEmpty()) {
            setScheduleMessage(errors.joinToString(separator = "\n"))
            return
        }
        if (parsedSchedules.size != pump.schedules.size) {
            setScheduleMessage(text(R.string.vm_schedule_refresh_required))
            return
        }

        val update = PumpConfigUpdate(
            pumpIndex = pump.id,
            name = pump.savedNameOrFallback(),
            enabled = pump.enabled,
            mlPerSec = pump.mlPerSec,
            schedules = parsedSchedules,
        )

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(scheduleSaveInProgress = true, scheduleMessage = text(R.string.vm_schedule_saving))
            }
            logPumpEndpoint(
                endpoint = "/savePump?p=${pump.id}&name=...&enabled=...&rate=...&s0...s7...",
                reason = "Save complete pump configuration for pump ${pump.id + 1}; preserves name, enabled state, calibration rate, and all unchanged schedule slots",
            )
            val message = when (val result = controllerRepository.savePumpConfig(state.controllerAddress, update)) {
                is AquaDoseResult.Success -> text(R.string.vm_schedule_saved)
                is AquaDoseResult.HttpFailure -> text(R.string.vm_schedule_failed)
                is AquaDoseResult.NetworkFailure -> text(R.string.vm_schedule_unreachable)
                is AquaDoseResult.UnexpectedFailure -> text(R.string.vm_schedule_unreadable)
                is AquaDoseResult.ValidationFailure -> text(R.string.vm_schedule_validation_failed)
            }
            mutableUiState.update {
                it.copy(scheduleSaveInProgress = false, scheduleMessage = message)
            }
            refreshStatus(markRefreshing = false)
        }
    }

    fun setPumpScheduledDosingAllowed(pumpId: Int, allowed: Boolean) {
        val state = uiState.value
        if (state.pumpScheduleToggleInProgress != null) return
        val pump = state.controllerStatus?.pumps?.firstOrNull { it.id == pumpId }
            ?: return setPumpActionMessage(text(R.string.vm_schedule_permission_no_pump))
        if (pump.enabled == allowed) return
        if (state.controllerAddress.isBlank()) {
            setPumpActionMessage(text(R.string.vm_action_address_required))
            return
        }
        val invalidScheduleSlots = pump.scheduleSlotsWithInvalidDoses()
        if (allowed && invalidScheduleSlots.isNotEmpty()) {
            setPumpActionMessage(
                text(
                    R.string.vm_schedule_permission_dose_invalid,
                    invalidScheduleSlots.joinToString(),
                    doseMinLabel(),
                    doseMaxLabel(),
                ),
            )
            return
        }

        val previousStatus = state.controllerStatus
        val update = PumpConfigUpdate.fromStatusPump(pump).copy(
            name = pump.savedNameOrFallback(),
            enabled = allowed,
        )

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    pumpScheduleToggleInProgress = pumpId,
                    pumpActionMessage = text(R.string.vm_schedule_permission_saving),
                )
            }
            logPumpEndpoint(
                endpoint = "/savePump?p=${pump.id}&enabled=${if (allowed) "1" else "0"}&name=...&rate=...&s0...s7...",
                reason = "Update scheduled dosing permission for pump ${pump.id + 1}; preserves name, calibration rate, and all schedule slots",
            )
            val result = controllerRepository.savePumpConfig(state.controllerAddress, update)
            when (result) {
                is AquaDoseResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            pumpScheduleToggleInProgress = null,
                            pumpActionMessage = if (allowed) {
                                text(R.string.vm_schedule_permission_allowed)
                            } else {
                                text(R.string.vm_schedule_permission_blocked)
                            },
                        )
                    }
                    refreshStatus(markRefreshing = false, syncScheduleDrafts = true)
                }

                is AquaDoseResult.HttpFailure -> restorePumpScheduleToggle(previousStatus, text(R.string.vm_schedule_permission_failed))
                is AquaDoseResult.NetworkFailure -> restorePumpScheduleToggle(previousStatus, text(R.string.vm_schedule_permission_unreachable))
                is AquaDoseResult.UnexpectedFailure -> restorePumpScheduleToggle(previousStatus, text(R.string.vm_schedule_permission_unreadable))
                is AquaDoseResult.ValidationFailure -> restorePumpScheduleToggle(previousStatus, text(R.string.vm_schedule_permission_rejected))
            }
        }
    }

    fun saveTimezone() {
        val state = uiState.value
        val timezone = state.timezoneInput.trim()
        if (timezone.isBlank()) {
            setSettingsMessage(text(R.string.vm_timezone_empty))
            return
        }
        runSettingsAction(
            busyMessage = text(R.string.vm_timezone_saving),
            endpoint = "/saveTZ?tz=$timezone",
            reason = "Save timezone from Settings screen",
            successMessage = text(R.string.vm_timezone_saved),
        ) {
            controllerRepository.saveTimezone(state.controllerAddress, timezone)
        }
    }

    fun resetWifi() {
        val state = uiState.value
        runSettingsAction(
            busyMessage = text(R.string.vm_wifi_resetting),
            endpoint = "/reset",
            reason = "Reset WiFi after user confirmation from Settings screen",
            successMessage = text(R.string.vm_wifi_reset_sent),
        ) {
            controllerRepository.resetWifi(state.controllerAddress)
        }
    }

    fun factoryReset() {
        val state = uiState.value
        if (state.settingsActionInProgress) return
        if (state.controllerAddress.isBlank()) {
            setSettingsMessage(text(R.string.vm_action_address_required))
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    settingsActionInProgress = true,
                    settingsMessage = text(R.string.vm_factory_resetting),
                )
            }
            logPumpEndpoint("/factoryReset", "Factory reset after double confirmation from Settings screen")
            when (controllerRepository.factoryReset(state.controllerAddress)) {
                is AquaDoseResult.Success -> completeFactoryResetFlow()
                is AquaDoseResult.HttpFailure -> {
                    mutableUiState.update {
                        it.copy(settingsActionInProgress = false, settingsMessage = text(R.string.vm_action_failed))
                    }
                }
                is AquaDoseResult.NetworkFailure -> {
                    mutableUiState.update {
                        it.copy(settingsActionInProgress = false, settingsMessage = text(R.string.vm_action_unreachable))
                    }
                }
                is AquaDoseResult.UnexpectedFailure -> {
                    mutableUiState.update {
                        it.copy(settingsActionInProgress = false, settingsMessage = text(R.string.vm_action_unreadable))
                    }
                }
                is AquaDoseResult.ValidationFailure -> {
                    mutableUiState.update {
                        it.copy(settingsActionInProgress = false, settingsMessage = text(R.string.vm_action_rejected))
                    }
                }
            }
        }
    }

    private fun runPumpAction(
        busyMessage: String,
        endpoint: String,
        reason: String,
        successMessage: String,
        action: suspend () -> AquaDoseResult<String>,
    ) {
        if (uiState.value.pumpActionInProgress) return
        if (uiState.value.controllerAddress.isBlank()) {
            setPumpActionMessage(text(R.string.vm_action_address_required))
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(pumpActionInProgress = true, pumpActionMessage = busyMessage)
            }
            logPumpEndpoint(endpoint, reason)
            val message = when (val result = action()) {
                is AquaDoseResult.Success -> successMessage
                is AquaDoseResult.HttpFailure -> text(R.string.vm_action_failed)
                is AquaDoseResult.NetworkFailure -> text(R.string.vm_action_unreachable)
                is AquaDoseResult.UnexpectedFailure -> text(R.string.vm_action_unreadable)
                is AquaDoseResult.ValidationFailure -> text(R.string.vm_action_rejected)
            }
            mutableUiState.update {
                it.copy(pumpActionInProgress = false, pumpActionMessage = message)
            }
            refreshStatus(markRefreshing = false)
        }
    }

    private fun runSettingsAction(
        busyMessage: String,
        endpoint: String,
        reason: String,
        successMessage: String,
        action: suspend () -> AquaDoseResult<String>,
    ) {
        if (uiState.value.settingsActionInProgress) return
        if (uiState.value.controllerAddress.isBlank()) {
            setSettingsMessage(text(R.string.vm_action_address_required))
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(settingsActionInProgress = true, settingsMessage = busyMessage)
            }
            logPumpEndpoint(endpoint, reason)
            val message = when (val result = action()) {
                is AquaDoseResult.Success -> successMessage
                is AquaDoseResult.HttpFailure -> text(R.string.vm_action_failed)
                is AquaDoseResult.NetworkFailure -> text(R.string.vm_action_unreachable)
                is AquaDoseResult.UnexpectedFailure -> text(R.string.vm_action_unreadable)
                is AquaDoseResult.ValidationFailure -> text(R.string.vm_action_rejected)
            }
            mutableUiState.update {
                it.copy(settingsActionInProgress = false, settingsMessage = message)
            }
            refreshStatus(markRefreshing = false)
        }
    }

    private fun setPumpActionMessage(message: String) {
        mutableUiState.update { it.copy(pumpActionMessage = message) }
    }

    private fun setPumpSettingsMessage(message: String) {
        mutableUiState.update { it.copy(pumpSettingsMessage = message) }
    }

    private fun setScheduleMessage(message: String) {
        mutableUiState.update { it.copy(scheduleMessage = message) }
    }

    private fun setSettingsMessage(message: String) {
        mutableUiState.update { it.copy(settingsMessage = message) }
    }

    private fun restorePumpScheduleToggle(previousStatus: AquaDoseStatus?, message: String) {
        mutableUiState.update {
            it.copy(
                controllerStatus = previousStatus,
                pumpScheduleToggleInProgress = null,
                pumpActionMessage = message,
            )
        }
    }

    private fun restorePumpSettingsSave(message: String) {
        mutableUiState.update {
            it.copy(
                pumpSettingsSaveInProgress = false,
                pumpSettingsMessage = message,
            )
        }
    }

    private fun completeFactoryResetFlow() {
        stopStatusPolling()
        val setupAddress = controllerRepository.saveSetupControllerIp()
        mutableUiState.update {
            it.copy(
                controllerAddress = setupAddress,
                currentScreen = AppScreen.Dashboard,
                selectedPumpId = null,
                isCheckingConnection = false,
                connectionStatus = ConnectionStatus.NotConnected,
                statusSummary = text(R.string.vm_factory_reset_completed_body, setupAddress),
                rawErrorDetails = "",
                controllerStatus = null,
                isRefreshingStatus = false,
                lastStatusMessage = text(R.string.vm_factory_reset_completed_title),
                emergencyStopInProgress = false,
                manualDoseMl = "",
                calibrationGrams = "",
                pumpNameDraft = "",
                pumpCalibrationRateDraft = "",
                pumpSettingsSaveInProgress = false,
                pumpSettingsMessage = "",
                pumpActionInProgress = false,
                pumpScheduleToggleInProgress = null,
                pumpActionMessage = "",
                scheduleDrafts = emptyList(),
                scheduleSaveInProgress = false,
                scheduleMessage = "",
                settingsControllerAddress = setupAddress,
                timezoneInput = "",
                settingsActionInProgress = false,
                settingsMessage = "",
            )
        }
    }

    private fun logPumpEndpoint(endpoint: String, reason: String) {
        if (BuildConfig.DEBUG) {
            Log.i("AquaDosePumpAction", "Calling $endpoint because: $reason")
        }
    }

    private suspend fun refreshStatus(
        markRefreshing: Boolean,
        syncSelectedPumpDraft: Boolean = false,
        syncScheduleDrafts: Boolean = false,
    ) {
        val address = uiState.value.controllerAddress.trim()
        if (address.isEmpty()) {
            mutableUiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    rawErrorDetails = text(R.string.vm_error_address_required_refresh),
                    lastStatusMessage = text(R.string.vm_status_refresh_paused),
                    isRefreshingStatus = false,
                )
            }
            return
        }

        if (markRefreshing) {
            mutableUiState.update {
                it.copy(
                    isRefreshingStatus = true,
                    lastStatusMessage = text(R.string.vm_status_refreshing),
                )
            }
        }

        when (val result = controllerRepository.refreshStatus(address)) {
            is AquaDoseResult.Success -> {
                val status = result.value
                mutableUiState.update { current ->
                    val selectedPump = if (syncSelectedPumpDraft || syncScheduleDrafts) {
                        status.pumps.firstOrNull { pump -> pump.id == current.selectedPumpId }
                    } else {
                        null
                    }
                    current.copy(
                        connectionStatus = ConnectionStatus.Connected,
                        controllerStatus = status,
                        statusSummary = text(R.string.vm_status_updated_summary, status.pumps.size, status.currentTime),
                        rawErrorDetails = "",
                        lastStatusMessage = text(R.string.vm_status_updated),
                        isRefreshingStatus = false,
                        pumpNameDraft = selectedPump?.name?.ifBlank { text(R.string.pump_title, selectedPump.id + 1) }
                            ?: current.pumpNameDraft,
                        pumpCalibrationRateDraft = selectedPump?.mlPerSec?.toRateDraft()
                            ?: current.pumpCalibrationRateDraft,
                        scheduleDrafts = if (syncScheduleDrafts) {
                            selectedPump?.schedules?.map { schedule -> schedule.toDraft() } ?: current.scheduleDrafts
                        } else {
                            current.scheduleDrafts
                        },
                    )
                }
            }

            is AquaDoseResult.HttpFailure -> {
                mutableUiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        rawErrorDetails = text(R.string.vm_error_controller_response_refresh),
                        lastStatusMessage = text(R.string.vm_status_controller_error),
                        isRefreshingStatus = false,
                    )
                }
            }

            is AquaDoseResult.NetworkFailure -> {
                mutableUiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        rawErrorDetails = text(R.string.vm_error_controller_unreachable),
                        lastStatusMessage = text(R.string.vm_status_controller_offline),
                        isRefreshingStatus = false,
                    )
                }
            }

            is AquaDoseResult.UnexpectedFailure -> {
                mutableUiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        rawErrorDetails = text(R.string.vm_error_controller_unreadable),
                        lastStatusMessage = text(R.string.vm_status_controller_unreadable),
                        isRefreshingStatus = false,
                    )
                }
            }

            is AquaDoseResult.ValidationFailure -> {
                mutableUiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        rawErrorDetails = text(R.string.vm_error_controller_incomplete),
                        lastStatusMessage = text(R.string.vm_status_controller_attention),
                        isRefreshingStatus = false,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopStatusPolling()
        super.onCleared()
    }
}

private fun AquaDosePump.scheduleSlotsWithInvalidDoses(): List<Int> =
    schedules.mapIndexedNotNull { index, schedule ->
        if (schedule.enabled && AquaDoseValidation.validateMl(schedule.ml) != null) index + 1 else null
    }

private fun AquaDosePump.savedNameOrFallback(): String =
    name.ifBlank { "Pump ${id + 1}" }

private fun AquaDoseSchedule.toDraft(): ScheduleSlotDraft {
    val hour = hour.toString()
    val minute = minute.toString()
    val ml = ml.toString()
    return ScheduleSlotDraft(
        enabled = enabled,
        hour = hour,
        minute = minute,
        ml = ml,
        savedEnabled = enabled,
        savedHour = hour,
        savedMinute = minute,
        savedMl = ml,
    )
}

private fun Double.toRateDraft(): String =
    if (this >= 0.01) {
        String.format(Locale.US, "%.4f", this).trimTrailingZeros()
    } else {
        ""
    }

private fun String.trimTrailingZeros(): String =
    if (contains('.')) trimEnd('0').trimEnd('.') else this
