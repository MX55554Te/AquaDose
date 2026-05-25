package com.aquadose.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aquadose.api.AquaDosePump
import com.aquadose.api.AquaDoseStatus
import com.aquadose.api.AquaDoseValidation
import com.aquadose.app.R
import com.aquadose.app.ui.theme.AquaDoseTheme

private object AquaSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

private object AquaShape {
    val card = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(10.dp)
    val badge = RoundedCornerShape(999.dp)
}

private enum class AquaWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

private val SuccessContainer = Color(0xFFD7F2E3)
private val OnSuccessContainer = Color(0xFF103822)
private val WarningContainer = Color(0xFFFFF2CC)
private val OnWarningContainer = Color(0xFF493700)
private const val MAX_PUMP_NAME_LENGTH = 31
private const val MAX_DIRECT_CALIBRATION_RATE_ML_PER_SEC = 100.0
@Composable
fun AquaDoseApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.startStatusPolling()
        onDispose { viewModel.stopStatusPolling() }
    }

    AquaDoseTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthClass = when {
                maxWidth < 600.dp -> AquaWindowWidthClass.Compact
                maxWidth < 840.dp -> AquaWindowWidthClass.Medium
                else -> AquaWindowWidthClass.Expanded
            }
            AquaDoseScaffold(
                currentScreen = uiState.currentScreen,
                widthClass = widthClass,
                onShowDashboard = viewModel::showDashboard,
                onShowPumps = viewModel::showPumps,
                onShowLogs = viewModel::showLogs,
                onShowSettings = viewModel::showSettings,
            ) { contentPadding ->
                AppContent(
                    uiState = uiState,
                    widthClass = widthClass,
                    contentPadding = contentPadding,
                    onAddressChanged = viewModel::onControllerAddressChanged,
                    onUseSuggested = viewModel::useSuggestedController,
                    onUseSetupAp = viewModel::useSetupAp,
                    onCheckConnection = viewModel::checkConnection,
                    onManualRefresh = viewModel::manualRefresh,
                    onEmergencyStop = viewModel::emergencyStop,
                    onShowPumps = viewModel::showPumps,
                    onPumpSelected = viewModel::showPumpDetail,
                    onManualDoseMlChanged = viewModel::onManualDoseMlChanged,
                    onCalibrationGramsChanged = viewModel::onCalibrationGramsChanged,
                    onPumpNameChanged = viewModel::onPumpNameDraftChanged,
                    onPumpCalibrationRateChanged = viewModel::onPumpCalibrationRateDraftChanged,
                    onSavePumpSettings = viewModel::savePumpSettings,
                    onPumpScheduledDosingAllowedChanged = viewModel::setPumpScheduledDosingAllowed,
                    onStartManualDose = viewModel::startManualDose,
                    onStartPrime = viewModel::startPrime,
                    onStopPrime = viewModel::stopPrime,
                    onRunCalibration = viewModel::runCalibration,
                    onSaveCalibration = viewModel::saveCalibration,
                    onShowScheduleEditor = viewModel::showScheduleEditor,
                    onScheduleEnabledChanged = viewModel::onScheduleEnabledChanged,
                    onScheduleHourChanged = viewModel::onScheduleHourChanged,
                    onScheduleMinuteChanged = viewModel::onScheduleMinuteChanged,
                    onScheduleMlChanged = viewModel::onScheduleMlChanged,
                    onSaveSchedules = viewModel::saveSchedules,
                    onSettingsControllerAddressChanged = viewModel::onSettingsControllerAddressChanged,
                    onTimezoneInputChanged = viewModel::onTimezoneInputChanged,
                    onSaveControllerAddress = viewModel::saveControllerAddressFromSettings,
                    onClearSavedControllerAddress = viewModel::clearSavedControllerAddress,
                    onSaveTimezone = viewModel::saveTimezone,
                    onResetWifi = viewModel::resetWifi,
                    onFactoryReset = viewModel::factoryReset,
                )
            }
        }
    }
}

@Composable
private fun AquaDoseScaffold(
    currentScreen: AppScreen,
    widthClass: AquaWindowWidthClass,
    onShowDashboard: () -> Unit,
    onShowPumps: () -> Unit,
    onShowLogs: () -> Unit,
    onShowSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val selectedScreen = selectedMainScreen(currentScreen)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (widthClass == AquaWindowWidthClass.Compact) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainNavItem(
                        label = stringResource(R.string.nav_dashboard),
                        icon = Icons.Filled.Home,
                        selected = selectedScreen == AppScreen.Dashboard,
                        onClick = onShowDashboard,
                    )
                    MainNavItem(
                        label = stringResource(R.string.nav_pumps),
                        icon = Icons.Filled.Build,
                        selected = selectedScreen == AppScreen.Pumps,
                        onClick = onShowPumps,
                    )
                    MainNavItem(
                        label = stringResource(R.string.nav_logs),
                        icon = Icons.AutoMirrored.Filled.List,
                        selected = selectedScreen == AppScreen.Logs,
                        onClick = onShowLogs,
                    )
                    MainNavItem(
                        label = stringResource(R.string.nav_settings),
                        icon = Icons.Filled.Settings,
                        selected = selectedScreen == AppScreen.Settings,
                        onClick = onShowSettings,
                    )
                }
            }
        },
    ) { padding ->
        if (widthClass == AquaWindowWidthClass.Compact) {
            content(padding)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(AquaSpace.md))
                    RailNavItem(
                        label = stringResource(R.string.nav_dashboard),
                        icon = Icons.Filled.Home,
                        selected = selectedScreen == AppScreen.Dashboard,
                        onClick = onShowDashboard,
                    )
                    RailNavItem(
                        label = stringResource(R.string.nav_pumps),
                        icon = Icons.Filled.Build,
                        selected = selectedScreen == AppScreen.Pumps,
                        onClick = onShowPumps,
                    )
                    RailNavItem(
                        label = stringResource(R.string.nav_logs),
                        icon = Icons.AutoMirrored.Filled.List,
                        selected = selectedScreen == AppScreen.Logs,
                        onClick = onShowLogs,
                    )
                    RailNavItem(
                        label = stringResource(R.string.nav_settings),
                        icon = Icons.Filled.Settings,
                        selected = selectedScreen == AppScreen.Settings,
                        onClick = onShowSettings,
                    )
                }
                VerticalDivider()
                Box(Modifier.fillMaxSize()) {
                    content(PaddingValues())
                }
            }
        }
    }
}

@Composable
private fun RowScope.MainNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun RailNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

private fun selectedMainScreen(screen: AppScreen): AppScreen =
    when (screen) {
        AppScreen.PumpDetail, AppScreen.ScheduleEditor -> AppScreen.Pumps
        else -> screen
    }

@Composable
private fun AppContent(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onAddressChanged: (String) -> Unit,
    onUseSuggested: () -> Unit,
    onUseSetupAp: () -> Unit,
    onCheckConnection: () -> Unit,
    onManualRefresh: () -> Unit,
    onEmergencyStop: () -> Unit,
    onShowPumps: () -> Unit,
    onPumpSelected: (Int) -> Unit,
    onManualDoseMlChanged: (String) -> Unit,
    onCalibrationGramsChanged: (String) -> Unit,
    onPumpNameChanged: (String) -> Unit,
    onPumpCalibrationRateChanged: (String) -> Unit,
    onSavePumpSettings: () -> Unit,
    onPumpScheduledDosingAllowedChanged: (Int, Boolean) -> Unit,
    onStartManualDose: () -> Unit,
    onStartPrime: () -> Unit,
    onStopPrime: () -> Unit,
    onRunCalibration: () -> Unit,
    onSaveCalibration: () -> Unit,
    onShowScheduleEditor: (Int) -> Unit,
    onScheduleEnabledChanged: (Int, Boolean) -> Unit,
    onScheduleHourChanged: (Int, String) -> Unit,
    onScheduleMinuteChanged: (Int, String) -> Unit,
    onScheduleMlChanged: (Int, String) -> Unit,
    onSaveSchedules: () -> Unit,
    onSettingsControllerAddressChanged: (String) -> Unit,
    onTimezoneInputChanged: (String) -> Unit,
    onSaveControllerAddress: () -> Unit,
    onClearSavedControllerAddress: () -> Unit,
    onSaveTimezone: () -> Unit,
    onResetWifi: () -> Unit,
    onFactoryReset: () -> Unit,
) {
    var emergencyStopConfirm by remember { mutableStateOf(false) }
    var pumpActionConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var resetWifiConfirm by remember { mutableStateOf(false) }
    var factoryResetFirstConfirm by remember { mutableStateOf(false) }
    var factoryResetSecondConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.currentScreen == AppScreen.PumpDetail && widthClass == AquaWindowWidthClass.Compact) {
        onShowPumps()
    }
    BackHandler(enabled = uiState.currentScreen == AppScreen.ScheduleEditor) {
        uiState.selectedPumpId?.let(onPumpSelected) ?: onShowPumps()
    }

    val manualDoseCopy = ManualDoseDialogCopy(
        invalidTitle = stringResource(R.string.dialog_invalid_dose_title),
        invalidBody = stringResource(R.string.dialog_invalid_dose_body, doseMinLabel(), doseMaxLabel()),
        normalTitle = stringResource(R.string.dialog_manual_dose_title),
        largeTitle = stringResource(R.string.dialog_large_manual_dose_title),
        normalBody = stringResource(R.string.dialog_manual_dose_body),
        largeBody = stringResource(R.string.dialog_large_manual_dose_body),
        confirmLabel = stringResource(R.string.action_start_manual_dose),
        okLabel = stringResource(R.string.action_ok),
    )
    val startPrimeTitle = stringResource(R.string.dialog_start_prime_title)
    val startPrimeBody = stringResource(R.string.dialog_start_prime_body)
    val startPrimeConfirm = stringResource(R.string.action_start_prime)
    val runCalibrationTitle = stringResource(R.string.dialog_run_calibration_title)
    val runCalibrationBody = stringResource(R.string.dialog_run_calibration_body)
    val runCalibrationConfirm = stringResource(R.string.action_run_calibration)

    when (uiState.currentScreen) {
        AppScreen.Dashboard -> DashboardScreen(
            uiState = uiState,
            widthClass = widthClass,
            contentPadding = contentPadding,
            onAddressChanged = onAddressChanged,
            onUseSuggested = onUseSuggested,
            onUseSetupAp = onUseSetupAp,
            onCheckConnection = onCheckConnection,
            onManualRefresh = onManualRefresh,
            onEmergencyStopRequested = { emergencyStopConfirm = true },
        )

        AppScreen.Pumps -> {
            if (widthClass == AquaWindowWidthClass.Compact) {
                PumpsScreen(
                    uiState = uiState,
                    contentPadding = contentPadding,
                    onManualRefresh = onManualRefresh,
                    onPumpSelected = onPumpSelected,
                )
            } else {
                PumpsListDetailScreen(
                    uiState = uiState,
                    widthClass = widthClass,
                    contentPadding = contentPadding,
                    onManualRefresh = onManualRefresh,
                    onPumpSelected = onPumpSelected,
                    onEditSchedules = onShowScheduleEditor,
                    onEmergencyStopRequested = { emergencyStopConfirm = true },
                    onManualDoseMlChanged = onManualDoseMlChanged,
                    onCalibrationGramsChanged = onCalibrationGramsChanged,
                    onPumpNameChanged = onPumpNameChanged,
                    onPumpCalibrationRateChanged = onPumpCalibrationRateChanged,
                    onSavePumpSettings = onSavePumpSettings,
                    onPumpScheduledDosingAllowedChanged = onPumpScheduledDosingAllowedChanged,
                    onStartManualDoseRequested = {
                        pumpActionConfirm = manualDoseConfirm(uiState, onStartManualDose, manualDoseCopy)
                    },
                    onStartPrimeRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = startPrimeTitle,
                            text = startPrimeBody,
                            confirmLabel = startPrimeConfirm,
                            onConfirm = onStartPrime,
                        )
                    },
                    onStopPrime = onStopPrime,
                    onRunCalibrationRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = runCalibrationTitle,
                            text = runCalibrationBody,
                            confirmLabel = runCalibrationConfirm,
                            onConfirm = onRunCalibration,
                        )
                    },
                    onSaveCalibration = onSaveCalibration,
                )
            }
        }

        AppScreen.PumpDetail -> {
            if (widthClass == AquaWindowWidthClass.Compact) {
                PumpDetailScreen(
                    uiState = uiState,
                    contentPadding = contentPadding,
                    onBack = onShowPumps,
                    onEditSchedules = onShowScheduleEditor,
                    onEmergencyStopRequested = { emergencyStopConfirm = true },
                    onManualDoseMlChanged = onManualDoseMlChanged,
                    onCalibrationGramsChanged = onCalibrationGramsChanged,
                    onPumpNameChanged = onPumpNameChanged,
                    onPumpCalibrationRateChanged = onPumpCalibrationRateChanged,
                    onSavePumpSettings = onSavePumpSettings,
                    onPumpScheduledDosingAllowedChanged = onPumpScheduledDosingAllowedChanged,
                    onStartManualDoseRequested = {
                        pumpActionConfirm = manualDoseConfirm(uiState, onStartManualDose, manualDoseCopy)
                    },
                    onStartPrimeRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = startPrimeTitle,
                            text = startPrimeBody,
                            confirmLabel = startPrimeConfirm,
                            onConfirm = onStartPrime,
                        )
                    },
                    onStopPrime = onStopPrime,
                    onRunCalibrationRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = runCalibrationTitle,
                            text = runCalibrationBody,
                            confirmLabel = runCalibrationConfirm,
                            onConfirm = onRunCalibration,
                        )
                    },
                    onSaveCalibration = onSaveCalibration,
                )
            } else {
                PumpsListDetailScreen(
                    uiState = uiState,
                    widthClass = widthClass,
                    contentPadding = contentPadding,
                    onManualRefresh = onManualRefresh,
                    onPumpSelected = onPumpSelected,
                    onEditSchedules = onShowScheduleEditor,
                    onEmergencyStopRequested = { emergencyStopConfirm = true },
                    onManualDoseMlChanged = onManualDoseMlChanged,
                    onCalibrationGramsChanged = onCalibrationGramsChanged,
                    onPumpNameChanged = onPumpNameChanged,
                    onPumpCalibrationRateChanged = onPumpCalibrationRateChanged,
                    onSavePumpSettings = onSavePumpSettings,
                    onPumpScheduledDosingAllowedChanged = onPumpScheduledDosingAllowedChanged,
                    onStartManualDoseRequested = {
                        pumpActionConfirm = manualDoseConfirm(uiState, onStartManualDose, manualDoseCopy)
                    },
                    onStartPrimeRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = startPrimeTitle,
                            text = startPrimeBody,
                            confirmLabel = startPrimeConfirm,
                            onConfirm = onStartPrime,
                        )
                    },
                    onStopPrime = onStopPrime,
                    onRunCalibrationRequested = {
                        pumpActionConfirm = ConfirmAction(
                            title = runCalibrationTitle,
                            text = runCalibrationBody,
                            confirmLabel = runCalibrationConfirm,
                            onConfirm = onRunCalibration,
                        )
                    },
                    onSaveCalibration = onSaveCalibration,
                )
            }
        }

        AppScreen.ScheduleEditor -> ScheduleEditorScreen(
            uiState = uiState,
            widthClass = widthClass,
            contentPadding = contentPadding,
            onBack = { uiState.selectedPumpId?.let(onPumpSelected) ?: onShowPumps() },
            onScheduleEnabledChanged = onScheduleEnabledChanged,
            onScheduleHourChanged = onScheduleHourChanged,
            onScheduleMinuteChanged = onScheduleMinuteChanged,
            onScheduleMlChanged = onScheduleMlChanged,
            onSaveSchedules = onSaveSchedules,
        )

        AppScreen.Logs -> LogsScreen(
            uiState = uiState,
            widthClass = widthClass,
            contentPadding = contentPadding,
            onManualRefresh = onManualRefresh,
        )

        AppScreen.Settings -> SettingsScreen(
            uiState = uiState,
            widthClass = widthClass,
            contentPadding = contentPadding,
            onControllerAddressChanged = onSettingsControllerAddressChanged,
            onTimezoneInputChanged = onTimezoneInputChanged,
            onSaveControllerAddress = onSaveControllerAddress,
            onClearSavedControllerAddress = onClearSavedControllerAddress,
            onSaveTimezone = onSaveTimezone,
            onResetWifiRequested = { resetWifiConfirm = true },
            onFactoryResetRequested = { factoryResetFirstConfirm = true },
        )
    }

    ConfirmActionDialog(
        action = if (emergencyStopConfirm) {
            ConfirmAction(
                title = stringResource(R.string.dialog_stop_all_title),
                text = stringResource(R.string.dialog_stop_all_body),
                confirmLabel = stringResource(R.string.action_stop_all_pumps),
                onConfirm = onEmergencyStop,
                danger = true,
            )
        } else {
            null
        },
        onDismiss = { emergencyStopConfirm = false },
    )

    ConfirmActionDialog(
        action = pumpActionConfirm,
        onDismiss = { pumpActionConfirm = null },
    )

    ConfirmActionDialog(
        action = if (resetWifiConfirm) {
            ConfirmAction(
                title = stringResource(R.string.dialog_reset_wifi_title),
                text = stringResource(R.string.dialog_reset_wifi_body),
                confirmLabel = stringResource(R.string.action_reset_wifi),
                onConfirm = onResetWifi,
                danger = true,
            )
        } else {
            null
        },
        onDismiss = { resetWifiConfirm = false },
    )

    ConfirmActionDialog(
        action = if (factoryResetFirstConfirm) {
            ConfirmAction(
                title = stringResource(R.string.dialog_factory_reset_title),
                text = stringResource(R.string.dialog_factory_reset_body),
                confirmLabel = stringResource(R.string.action_continue),
                onConfirm = {
                    factoryResetFirstConfirm = false
                    factoryResetSecondConfirm = true
                },
                danger = true,
            )
        } else {
            null
        },
        onDismiss = { factoryResetFirstConfirm = false },
    )

    ConfirmActionDialog(
        action = if (factoryResetSecondConfirm) {
            ConfirmAction(
                title = stringResource(R.string.dialog_factory_reset_final_title),
                text = stringResource(R.string.dialog_factory_reset_final_body),
                confirmLabel = stringResource(R.string.action_factory_reset),
                onConfirm = onFactoryReset,
                danger = true,
            )
        } else {
            null
        },
        onDismiss = { factoryResetSecondConfirm = false },
    )
}

private data class ConfirmAction(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val danger: Boolean = false,
)

private data class ManualDoseDialogCopy(
    val invalidTitle: String,
    val invalidBody: String,
    val normalTitle: String,
    val largeTitle: String,
    val normalBody: String,
    val largeBody: String,
    val confirmLabel: String,
    val okLabel: String,
)

@Composable
private fun ConfirmActionDialog(action: ConfirmAction?, onDismiss: () -> Unit) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (action.danger) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(action.title) },
        text = { Text(action.text) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    action.onConfirm()
                },
                colors = if (action.danger) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(action.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DashboardScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onAddressChanged: (String) -> Unit,
    onUseSuggested: () -> Unit,
    onUseSetupAp: () -> Unit,
    onCheckConnection: () -> Unit,
    onManualRefresh: () -> Unit,
    onEmergencyStopRequested: () -> Unit,
) {
    ScreenList(contentPadding, widthClass) {
        item {
            ScreenHeader(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.dashboard_subtitle),
                status = connectionLabel(uiState.connectionStatus),
                statusTone = connectionTone(uiState.connectionStatus),
            )
        }
        item {
            StatusCard(uiState = uiState, widthClass = widthClass, onManualRefresh = onManualRefresh)
        }
        item {
            EmergencyStopCard(uiState, onEmergencyStopRequested)
        }
        if (uiState.controllerStatus == null || uiState.connectionStatus != ConnectionStatus.Connected) {
            item {
                ConnectionCard(
                    uiState = uiState,
                    onAddressChanged = onAddressChanged,
                    onUseSuggested = onUseSuggested,
                    onUseSetupAp = onUseSetupAp,
                    onCheckConnection = onCheckConnection,
                )
            }
        }
        item {
            ErrorBanner(uiState.rawErrorDetails)
        }
    }
}

@Composable
private fun StatusCard(uiState: MainUiState, widthClass: AquaWindowWidthClass, onManualRefresh: () -> Unit) {
    val status = uiState.controllerStatus
    val isOnline = uiState.connectionStatus == ConnectionStatus.Connected
    SectionCard(
        title = stringResource(R.string.section_controller_status),
        subtitle = uiState.lastStatusMessage,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AquaSpace.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isOnline) stringResource(R.string.status_controller_online) else stringResource(R.string.status_controller_offline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AquaSpace.xs))
                Text(
                    text = status?.let { pumpStateText(it) } ?: stringResource(R.string.status_waiting_for_controller),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(connectionLabel(uiState.connectionStatus), connectionTone(uiState.connectionStatus))
        }

        Spacer(Modifier.height(AquaSpace.lg))

        if (status == null) {
            EmptyState(
                title = stringResource(R.string.empty_no_controller_status_title),
                body = stringResource(R.string.empty_no_controller_status_body),
            )
        } else {
            DashboardMetricRow(
                widthClass = widthClass,
                firstLabel = stringResource(R.string.label_controller_time),
                firstValue = status.currentTime.ifBlank { stringResource(R.string.value_unknown) },
                secondLabel = stringResource(R.string.label_pump_state),
                secondValue = pumpStateText(status),
            )
            Spacer(Modifier.height(AquaSpace.md))
            InfoRow(stringResource(R.string.label_current_address), uiState.controllerAddress.ifBlank { stringResource(R.string.value_no_address_saved) })
            InfoRow(stringResource(R.string.label_timezone), status.tz.ifBlank { stringResource(R.string.value_not_set) })
            InfoRow(
                stringResource(R.string.label_time_validity),
                if (status.timeValid) stringResource(R.string.value_time_valid) else stringResource(R.string.value_time_invalid),
            )
            Spacer(Modifier.height(AquaSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
                StatusBadge(
                    if (status.timeValid) stringResource(R.string.badge_time_valid) else stringResource(R.string.badge_time_needs_attention),
                    if (status.timeValid) StatusTone.Success else StatusTone.Warning,
                )
                StatusBadge(
                    if (status.running) stringResource(R.string.badge_pump_running) else stringResource(R.string.badge_all_idle),
                    if (status.running) StatusTone.Warning else StatusTone.Success,
                )
            }
        }

        Spacer(Modifier.height(AquaSpace.lg))
        SecondaryActionButton(
            text = if (uiState.isRefreshingStatus) stringResource(R.string.action_refreshing_status) else stringResource(R.string.action_refresh_status),
            icon = Icons.Filled.Refresh,
            onClick = onManualRefresh,
            enabled = !uiState.isRefreshingStatus,
        )
    }
}

@Composable
private fun DashboardMetricRow(
    widthClass: AquaWindowWidthClass,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    if (widthClass == AquaWindowWidthClass.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
            MetricPanel(label = firstLabel, value = firstValue)
            MetricPanel(label = secondLabel, value = secondValue)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AquaSpace.md)) {
            MetricPanel(modifier = Modifier.weight(1f), label = firstLabel, value = firstValue)
            MetricPanel(modifier = Modifier.weight(1f), label = secondLabel, value = secondValue)
        }
    }
}

@Composable
private fun MetricPanel(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AquaShape.control,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(AquaSpace.md)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AquaSpace.xs))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConnectionCard(
    uiState: MainUiState,
    onAddressChanged: (String) -> Unit,
    onUseSuggested: () -> Unit,
    onUseSetupAp: () -> Unit,
    onCheckConnection: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.section_controller_connection),
        subtitle = stringResource(R.string.connection_subtitle),
        tone = if (uiState.connectionStatus == ConnectionStatus.Disconnected) SectionTone.Warning else SectionTone.Normal,
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.controllerAddress,
            onValueChange = onAddressChanged,
            label = { Text(stringResource(R.string.label_controller_address)) },
            placeholder = { Text(stringResource(R.string.example_controller_address)) },
            supportingText = { Text(stringResource(R.string.help_controller_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !uiState.isCheckingConnection,
        )
        Spacer(Modifier.height(AquaSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
            AssistChip(
                onClick = onUseSuggested,
                label = { Text(stringResource(R.string.action_use_default_address)) },
                enabled = !uiState.isCheckingConnection,
            )
            AssistChip(
                onClick = onUseSetupAp,
                label = { Text(stringResource(R.string.action_use_setup_address)) },
                enabled = !uiState.isCheckingConnection,
            )
        }
        Spacer(Modifier.height(AquaSpace.lg))
        PrimaryActionButton(
            text = if (uiState.isCheckingConnection) stringResource(R.string.action_connecting) else stringResource(R.string.action_connect_controller),
            loading = uiState.isCheckingConnection,
            onClick = onCheckConnection,
            enabled = !uiState.isCheckingConnection,
        )
        if (uiState.statusSummary.isNotBlank()) {
            Spacer(Modifier.height(AquaSpace.md))
            MessageCard(uiState.statusSummary)
        }
    }
}

@Composable
private fun PumpsScreen(
    uiState: MainUiState,
    contentPadding: PaddingValues,
    onManualRefresh: () -> Unit,
    onPumpSelected: (Int) -> Unit,
) {
    val pumps = uiState.controllerStatus?.pumps.orEmpty()
    ScreenList(contentPadding) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_pumps),
                subtitle = stringResource(R.string.pumps_subtitle),
                status = stringResource(R.string.status_pump_count, pumps.size),
            )
        }
        item {
            SecondaryActionButton(
                text = if (uiState.isRefreshingStatus) stringResource(R.string.action_refreshing_pumps) else stringResource(R.string.action_refresh_pumps),
                icon = Icons.Filled.Refresh,
                onClick = onManualRefresh,
                enabled = !uiState.isRefreshingStatus,
            )
        }
        if (pumps.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_no_pumps_title),
                    body = stringResource(R.string.empty_no_pumps_body),
                )
            }
        } else {
            items(pumps, key = { it.id }) { pump ->
                PumpCard(
                    pump = pump,
                    isRunning = uiState.controllerStatus?.running == true && uiState.controllerStatus.runningPump == pump.id,
                    onClick = { onPumpSelected(pump.id) },
                )
            }
        }
        item { ErrorBanner(uiState.rawErrorDetails) }
    }
}

@Composable
private fun PumpsListDetailScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onManualRefresh: () -> Unit,
    onPumpSelected: (Int) -> Unit,
    onEditSchedules: (Int) -> Unit,
    onEmergencyStopRequested: () -> Unit,
    onManualDoseMlChanged: (String) -> Unit,
    onCalibrationGramsChanged: (String) -> Unit,
    onPumpNameChanged: (String) -> Unit,
    onPumpCalibrationRateChanged: (String) -> Unit,
    onSavePumpSettings: () -> Unit,
    onPumpScheduledDosingAllowedChanged: (Int, Boolean) -> Unit,
    onStartManualDoseRequested: () -> Unit,
    onStartPrimeRequested: () -> Unit,
    onStopPrime: () -> Unit,
    onRunCalibrationRequested: () -> Unit,
    onSaveCalibration: () -> Unit,
) {
    val pumps = uiState.controllerStatus?.pumps.orEmpty()
    val firstPumpId = pumps.firstOrNull()?.id
    LaunchedEffect(widthClass, firstPumpId, uiState.selectedPumpId) {
        if (uiState.selectedPumpId == null && firstPumpId != null) {
            onPumpSelected(firstPumpId)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = screenHorizontalPadding(widthClass),
                top = contentPadding.calculateTopPadding() + AquaSpace.lg,
                end = screenHorizontalPadding(widthClass),
                bottom = contentPadding.calculateBottomPadding() + AquaSpace.xl,
            ),
        horizontalArrangement = Arrangement.spacedBy(AquaSpace.lg),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 280.dp, max = if (widthClass == AquaWindowWidthClass.Medium) 320.dp else 380.dp),
            verticalArrangement = Arrangement.spacedBy(AquaSpace.lg),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_pumps),
                    subtitle = stringResource(R.string.pumps_subtitle),
                    status = stringResource(R.string.status_pump_count, pumps.size),
                )
            }
            item {
                SecondaryActionButton(
                    text = if (uiState.isRefreshingStatus) stringResource(R.string.action_refreshing_pumps) else stringResource(R.string.action_refresh_pumps),
                    icon = Icons.Filled.Refresh,
                    onClick = onManualRefresh,
                    enabled = !uiState.isRefreshingStatus,
                )
            }
            if (pumps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.empty_no_pumps_title),
                        body = stringResource(R.string.empty_no_pumps_body),
                    )
                }
            } else {
                items(pumps, key = { it.id }) { pump ->
                    PumpCard(
                        pump = pump,
                        isRunning = uiState.controllerStatus?.running == true && uiState.controllerStatus.runningPump == pump.id,
                        selected = pump.id == uiState.selectedPumpId,
                        onClick = { onPumpSelected(pump.id) },
                    )
                }
            }
            item { ErrorBanner(uiState.rawErrorDetails) }
        }
        VerticalDivider(Modifier.fillMaxHeight())
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            PumpDetailScreen(
                uiState = uiState,
                widthClass = widthClass,
                contentPadding = PaddingValues(),
                showBackButton = false,
                onBack = {},
                onEditSchedules = onEditSchedules,
                onEmergencyStopRequested = onEmergencyStopRequested,
                onManualDoseMlChanged = onManualDoseMlChanged,
                onCalibrationGramsChanged = onCalibrationGramsChanged,
                onPumpNameChanged = onPumpNameChanged,
                onPumpCalibrationRateChanged = onPumpCalibrationRateChanged,
                onSavePumpSettings = onSavePumpSettings,
                onPumpScheduledDosingAllowedChanged = onPumpScheduledDosingAllowedChanged,
                onStartManualDoseRequested = onStartManualDoseRequested,
                onStartPrimeRequested = onStartPrimeRequested,
                onStopPrime = onStopPrime,
                onRunCalibrationRequested = onRunCalibrationRequested,
                onSaveCalibration = onSaveCalibration,
            )
        }
    }
}

@Composable
private fun PumpCard(pump: AquaDosePump, isRunning: Boolean, selected: Boolean = false, onClick: () -> Unit) {
    val enabledScheduleCount = pump.schedules.count { it.enabled }
    SectionCard(
        modifier = Modifier.clickable(onClick = onClick),
        title = pump.name.ifBlank { stringResource(R.string.pump_title, pump.id + 1) },
        subtitle = stringResource(R.string.pump_card_subtitle, pump.id + 1, pump.pin),
        tone = if (selected) SectionTone.Selected else SectionTone.Normal,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
            StatusBadge(
                schedulePermissionText(pump.enabled),
                if (pump.enabled) StatusTone.Success else StatusTone.Warning,
            )
            StatusBadge(
                if (pump.mlPerSec >= 0.01) stringResource(R.string.badge_calibrated) else stringResource(R.string.badge_uncalibrated),
                if (pump.mlPerSec >= 0.01) StatusTone.Success else StatusTone.Warning,
            )
            StatusBadge(
                stringResource(R.string.badge_enabled_schedule_count, enabledScheduleCount),
                if (enabledScheduleCount > 0) StatusTone.Success else StatusTone.Neutral,
            )
        }
        if (isRunning) {
            Spacer(Modifier.height(AquaSpace.sm))
            StatusBadge(stringResource(R.string.badge_running_now), StatusTone.Warning)
        }
        Spacer(Modifier.height(AquaSpace.md))
        InfoRow(stringResource(R.string.label_gpio), pump.pin.toString())
        InfoRow(stringResource(R.string.label_calibration_rate), stringResource(R.string.value_ml_per_sec, pump.mlPerSec))
        Text(
            text = stringResource(R.string.action_open_pump_controls),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PumpDetailScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass = AquaWindowWidthClass.Compact,
    contentPadding: PaddingValues,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onEditSchedules: (Int) -> Unit,
    onEmergencyStopRequested: () -> Unit,
    onManualDoseMlChanged: (String) -> Unit,
    onCalibrationGramsChanged: (String) -> Unit,
    onPumpNameChanged: (String) -> Unit,
    onPumpCalibrationRateChanged: (String) -> Unit,
    onSavePumpSettings: () -> Unit,
    onPumpScheduledDosingAllowedChanged: (Int, Boolean) -> Unit,
    onStartManualDoseRequested: () -> Unit,
    onStartPrimeRequested: () -> Unit,
    onStopPrime: () -> Unit,
    onRunCalibrationRequested: () -> Unit,
    onSaveCalibration: () -> Unit,
) {
    val pump = uiState.controllerStatus?.pumps?.firstOrNull { it.id == uiState.selectedPumpId }
    ScreenList(contentPadding, widthClass) {
        if (showBackButton) {
            item {
                BackTextButton(stringResource(R.string.action_back_to_pumps), onBack)
            }
        }
        if (pump == null) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_pump_unavailable_title),
                    body = stringResource(R.string.empty_pump_unavailable_body),
                )
            }
            return@ScreenList
        }

        val isRunning = uiState.controllerStatus?.running == true && uiState.controllerStatus.runningPump == pump.id
        item {
            ScreenHeader(
                title = pump.name.ifBlank { stringResource(R.string.pump_title, pump.id + 1) },
                subtitle = stringResource(R.string.pump_detail_subtitle, pump.id + 1, pump.pin),
                status = if (isRunning) stringResource(R.string.badge_running_now) else schedulePermissionText(pump.enabled),
                statusTone = if (isRunning) StatusTone.Warning else if (pump.enabled) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            SectionCard(title = stringResource(R.string.section_pump_status), subtitle = stringResource(R.string.pump_status_subtitle)) {
                if (isRunning) {
                    WarningBanner(stringResource(R.string.warning_pump_running))
                    Spacer(Modifier.height(AquaSpace.md))
                }
                InfoRow(stringResource(R.string.label_scheduled_dosing), schedulePermissionValue(pump.enabled))
                InfoRow(stringResource(R.string.label_gpio), pump.pin.toString())
                InfoRow(stringResource(R.string.label_calibration_rate), stringResource(R.string.value_ml_per_sec, pump.mlPerSec))
                InfoRow(stringResource(R.string.label_status), if (isRunning) stringResource(R.string.value_running) else stringResource(R.string.value_idle))
                Spacer(Modifier.height(AquaSpace.md))
                Column(verticalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
                    StatusBadge(
                        if (pump.mlPerSec >= 0.01) stringResource(R.string.badge_calibrated) else stringResource(R.string.badge_uncalibrated),
                        if (pump.mlPerSec >= 0.01) StatusTone.Success else StatusTone.Warning,
                    )
                    StatusBadge(
                        schedulePermissionText(pump.enabled),
                        if (pump.enabled) StatusTone.Success else StatusTone.Warning,
                    )
                }
            }
        }
        item { EmergencyStopCard(uiState, onEmergencyStopRequested) }
        item {
            ManualDoseSection(
                uiState = uiState,
                pump = pump,
                onManualDoseMlChanged = onManualDoseMlChanged,
                onStartManualDoseRequested = onStartManualDoseRequested,
            )
        }
        item {
            PrimeSection(
                uiState = uiState,
                isRunning = isRunning,
                onStartPrimeRequested = onStartPrimeRequested,
                onStopPrime = onStopPrime,
            )
        }
        item {
            CalibrationSection(
                uiState = uiState,
                onCalibrationGramsChanged = onCalibrationGramsChanged,
                onRunCalibrationRequested = onRunCalibrationRequested,
                onSaveCalibration = onSaveCalibration,
            )
        }
        item {
            SectionCard(title = stringResource(R.string.section_configuration), subtitle = stringResource(R.string.configuration_subtitle)) {
                PumpSettingsEditor(
                    uiState = uiState,
                    onPumpNameChanged = onPumpNameChanged,
                    onPumpCalibrationRateChanged = onPumpCalibrationRateChanged,
                    onSavePumpSettings = onSavePumpSettings,
                )
                Spacer(Modifier.height(AquaSpace.lg))
                PumpSchedulePermissionControl(
                    pump = pump,
                    isUpdating = uiState.pumpScheduleToggleInProgress == pump.id,
                    onAllowedChanged = { allowed -> onPumpScheduledDosingAllowedChanged(pump.id, allowed) },
                )
                Spacer(Modifier.height(AquaSpace.lg))
                SecondaryActionButton(
                    text = stringResource(R.string.action_edit_schedules),
                    icon = Icons.Filled.Schedule,
                    onClick = { onEditSchedules(pump.id) },
                )
            }
        }
        if (uiState.pumpActionInProgress) {
            item {
                LoadingState(uiState.pumpActionMessage.ifBlank { stringResource(R.string.loading_pump_action) })
            }
        }
        item { MessageCard(uiState.pumpActionMessage) }
    }
}

@Composable
private fun PumpSettingsEditor(
    uiState: MainUiState,
    onPumpNameChanged: (String) -> Unit,
    onPumpCalibrationRateChanged: (String) -> Unit,
    onSavePumpSettings: () -> Unit,
) {
    val trimmedName = uiState.pumpNameDraft.trim()
    val nameInvalid = trimmedName.isEmpty() || trimmedName.length > MAX_PUMP_NAME_LENGTH
    val rateText = uiState.pumpCalibrationRateDraft.trim()
    val rateValue = rateText.toDoubleOrNull()
    val rateInvalid = rateText.isNotBlank() &&
        (rateValue == null || rateValue < 0.0 || rateValue > MAX_DIRECT_CALIBRATION_RATE_ML_PER_SEC)

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.pumpNameDraft,
        onValueChange = onPumpNameChanged,
        label = { Text(stringResource(R.string.label_pump_name)) },
        supportingText = { Text(stringResource(R.string.help_pump_name)) },
        singleLine = true,
        enabled = !uiState.pumpSettingsSaveInProgress,
        isError = nameInvalid,
    )
    if (nameInvalid) {
        Spacer(Modifier.height(AquaSpace.sm))
        ErrorBanner(stringResource(R.string.error_pump_name_invalid))
    }
    Spacer(Modifier.height(AquaSpace.md))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.pumpCalibrationRateDraft,
        onValueChange = onPumpCalibrationRateChanged,
        label = { Text(stringResource(R.string.label_calibration_rate_ml_per_sec)) },
        supportingText = { Text(stringResource(R.string.help_calibration_rate_direct)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        enabled = !uiState.pumpSettingsSaveInProgress,
        isError = rateInvalid,
    )
    if (rateInvalid) {
        Spacer(Modifier.height(AquaSpace.sm))
        ErrorBanner(stringResource(R.string.error_calibration_rate_invalid))
    }
    Spacer(Modifier.height(AquaSpace.lg))
    PrimaryActionButton(
        text = if (uiState.pumpSettingsSaveInProgress) {
            stringResource(R.string.action_saving_pump_settings)
        } else {
            stringResource(R.string.action_save_pump_settings)
        },
        icon = Icons.Filled.Save,
        loading = uiState.pumpSettingsSaveInProgress,
        onClick = onSavePumpSettings,
        enabled = !uiState.pumpSettingsSaveInProgress && !nameInvalid && !rateInvalid,
    )
    if (uiState.pumpSettingsMessage.isNotBlank()) {
        Spacer(Modifier.height(AquaSpace.md))
        Text(uiState.pumpSettingsMessage, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PumpSchedulePermissionControl(
    pump: AquaDosePump,
    isUpdating: Boolean,
    onAllowedChanged: (Boolean) -> Unit,
) {
    val enabledScheduleCount = pump.schedules.count { it.enabled }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquaSpace.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.label_allow_scheduled_dosing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AquaSpace.xs))
            Text(
                text = stringResource(R.string.help_allow_scheduled_dosing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = pump.enabled,
            onCheckedChange = onAllowedChanged,
            enabled = !isUpdating,
        )
    }
    Spacer(Modifier.height(AquaSpace.md))
    StatusBadge(
        schedulePermissionText(pump.enabled),
        if (pump.enabled) StatusTone.Success else StatusTone.Warning,
    )
    if (isUpdating) {
        Spacer(Modifier.height(AquaSpace.md))
        LoadingState(stringResource(R.string.loading_schedule_permission))
    }
    if (!pump.enabled && enabledScheduleCount > 0) {
        Spacer(Modifier.height(AquaSpace.md))
        WarningBanner(stringResource(R.string.warning_schedules_blocked))
    }
}

@Composable
private fun ManualDoseSection(
    uiState: MainUiState,
    pump: AquaDosePump,
    onManualDoseMlChanged: (String) -> Unit,
    onStartManualDoseRequested: () -> Unit,
) {
    val doseValue = uiState.manualDoseMl.toDoubleOrNull()
    val hasInput = uiState.manualDoseMl.isNotBlank()
    val invalidDose = hasInput && (doseValue == null || AquaDoseValidation.validateMl(doseValue) != null)
    val canStart = !uiState.pumpActionInProgress && doseValue != null && AquaDoseValidation.validateMl(doseValue) == null

    SectionCard(title = stringResource(R.string.section_manual_dose), subtitle = stringResource(R.string.manual_dose_subtitle)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.manualDoseMl,
            onValueChange = onManualDoseMlChanged,
            label = { Text(stringResource(R.string.label_dose_amount)) },
            supportingText = { Text(stringResource(R.string.help_dose_amount, doseMinLabel(), doseMaxLabel())) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !uiState.pumpActionInProgress,
            isError = invalidDose,
        )
        if (invalidDose) {
            Spacer(Modifier.height(AquaSpace.sm))
            ErrorBanner(stringResource(R.string.error_invalid_manual_dose, doseMinLabel(), doseMaxLabel()))
        }
        if (pump.mlPerSec < 0.01) {
            Spacer(Modifier.height(AquaSpace.md))
            WarningBanner(stringResource(R.string.warning_uncalibrated_manual_dose))
        }
        Spacer(Modifier.height(AquaSpace.lg))
        PrimaryActionButton(
            text = stringResource(R.string.action_start_manual_dose),
            icon = Icons.Filled.PlayArrow,
            loading = uiState.pumpActionInProgress,
            onClick = onStartManualDoseRequested,
            enabled = canStart,
        )
    }
}

@Composable
private fun PrimeSection(
    uiState: MainUiState,
    isRunning: Boolean,
    onStartPrimeRequested: () -> Unit,
    onStopPrime: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_prime), subtitle = stringResource(R.string.prime_subtitle)) {
        SecondaryActionButton(
            text = stringResource(R.string.action_start_prime),
            icon = Icons.Filled.PlayArrow,
            onClick = onStartPrimeRequested,
            enabled = !uiState.pumpActionInProgress,
        )
        Spacer(Modifier.height(AquaSpace.sm))
        DangerButton(
            text = if (isRunning) stringResource(R.string.action_stop_running_pump) else stringResource(R.string.action_stop_prime),
            icon = Icons.Filled.Stop,
            onClick = onStopPrime,
            enabled = !uiState.pumpActionInProgress,
        )
    }
}

@Composable
private fun CalibrationSection(
    uiState: MainUiState,
    onCalibrationGramsChanged: (String) -> Unit,
    onRunCalibrationRequested: () -> Unit,
    onSaveCalibration: () -> Unit,
) {
    val gramsValue = uiState.calibrationGrams.toDoubleOrNull()
    val hasInput = uiState.calibrationGrams.isNotBlank()
    val invalidGrams = hasInput && (gramsValue == null || gramsValue <= 0.0)
    val canSave = !uiState.pumpActionInProgress && gramsValue != null && gramsValue > 0.0

    SectionCard(title = stringResource(R.string.section_calibration), subtitle = stringResource(R.string.calibration_subtitle)) {
        WarningBanner(stringResource(R.string.warning_calibration_run))
        Spacer(Modifier.height(AquaSpace.md))
        SecondaryActionButton(
            text = stringResource(R.string.action_run_calibration),
            icon = Icons.Filled.PlayArrow,
            onClick = onRunCalibrationRequested,
            enabled = !uiState.pumpActionInProgress,
        )
        Spacer(Modifier.height(AquaSpace.md))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.calibrationGrams,
            onValueChange = onCalibrationGramsChanged,
            label = { Text(stringResource(R.string.label_measured_grams)) },
            supportingText = { Text(stringResource(R.string.help_measured_grams)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !uiState.pumpActionInProgress,
            isError = invalidGrams,
        )
        if (invalidGrams) {
            Spacer(Modifier.height(AquaSpace.sm))
            ErrorBanner(stringResource(R.string.error_invalid_measured_grams))
        }
        Spacer(Modifier.height(AquaSpace.md))
        PrimaryActionButton(
            text = stringResource(R.string.action_save_calibration),
            icon = Icons.Filled.Save,
            onClick = onSaveCalibration,
            enabled = canSave,
        )
    }
}

@Composable
private fun ScheduleEditorScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onScheduleEnabledChanged: (Int, Boolean) -> Unit,
    onScheduleHourChanged: (Int, String) -> Unit,
    onScheduleMinuteChanged: (Int, String) -> Unit,
    onScheduleMlChanged: (Int, String) -> Unit,
    onSaveSchedules: () -> Unit,
) {
    val pump = uiState.controllerStatus?.pumps?.firstOrNull { it.id == uiState.selectedPumpId }
    ScreenList(contentPadding, widthClass) {
        item {
            BackTextButton(stringResource(R.string.action_back_to_pump), onBack)
        }
        item {
            ScreenHeader(
                title = pump?.let { stringResource(R.string.schedule_title, it.id + 1) } ?: stringResource(R.string.section_schedule_editor),
                subtitle = stringResource(R.string.schedule_editor_subtitle),
                status = stringResource(R.string.status_schedule_slots, uiState.scheduleDrafts.size),
            )
        }
        if (pump == null || uiState.scheduleDrafts.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_no_schedule_title),
                    body = stringResource(R.string.empty_no_schedule_body),
                )
            }
            return@ScreenList
        }
        itemsIndexed(uiState.scheduleDrafts) { index, draft ->
            ScheduleSlotCard(
                slotIndex = index,
                draft = draft,
                widthClass = widthClass,
                enabled = !uiState.scheduleSaveInProgress,
                onEnabledChanged = { onScheduleEnabledChanged(index, it) },
                onHourChanged = { onScheduleHourChanged(index, it) },
                onMinuteChanged = { onScheduleMinuteChanged(index, it) },
                onMlChanged = { onScheduleMlChanged(index, it) },
            )
        }
        item {
            PrimaryActionButton(
                text = if (uiState.scheduleSaveInProgress) stringResource(R.string.action_saving_schedule) else stringResource(R.string.action_save_schedule),
                icon = Icons.Filled.Save,
                loading = uiState.scheduleSaveInProgress,
                onClick = onSaveSchedules,
                enabled = !uiState.scheduleSaveInProgress,
            )
        }
        item { MessageCard(uiState.scheduleMessage) }
    }
}

@Composable
private fun ScheduleSlotCard(
    slotIndex: Int,
    draft: ScheduleSlotDraft,
    widthClass: AquaWindowWidthClass,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onHourChanged: (String) -> Unit,
    onMinuteChanged: (String) -> Unit,
    onMlChanged: (String) -> Unit,
) {
    val hourInvalid = draft.hour.toIntOrNull()?.let { it !in 0..23 } ?: true
    val minuteInvalid = draft.minute.toIntOrNull()?.let { it !in 0..59 } ?: true
    val mlValue = draft.ml.toDoubleOrNull()
    val mlInvalid = draft.enabled && (mlValue == null || AquaDoseValidation.validateMl(mlValue) != null)
    val hasUnsavedChanges = draft.hasUnsavedChanges()
    SectionCard(
        title = stringResource(R.string.schedule_slot_title, slotIndex + 1),
        subtitle = when {
            hasUnsavedChanges && draft.enabled -> stringResource(R.string.schedule_slot_pending_enabled)
            hasUnsavedChanges -> stringResource(R.string.schedule_slot_pending_disabled)
            draft.enabled -> stringResource(R.string.schedule_slot_enabled)
            else -> stringResource(R.string.schedule_slot_disabled)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        hasUnsavedChanges && draft.enabled -> stringResource(R.string.label_schedule_pending_enabled)
                        hasUnsavedChanges -> stringResource(R.string.label_schedule_pending_disabled)
                        draft.enabled -> stringResource(R.string.label_schedule_enabled)
                        else -> stringResource(R.string.label_schedule_disabled)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.help_schedule_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = draft.enabled, onCheckedChange = onEnabledChanged, enabled = enabled)
        }
        Spacer(Modifier.height(AquaSpace.md))
        if (widthClass == AquaWindowWidthClass.Compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
                ScheduleNumberField(
                    modifier = Modifier.weight(1f),
                    value = draft.hour,
                    onValueChange = onHourChanged,
                    label = stringResource(R.string.label_hour),
                    supportingText = stringResource(R.string.help_hour),
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    isError = hourInvalid,
                )
                ScheduleNumberField(
                    modifier = Modifier.weight(1f),
                    value = draft.minute,
                    onValueChange = onMinuteChanged,
                    label = stringResource(R.string.label_minute),
                    supportingText = stringResource(R.string.help_minute),
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    isError = minuteInvalid,
                )
            }
            Spacer(Modifier.height(AquaSpace.sm))
            ScheduleNumberField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.ml,
                onValueChange = onMlChanged,
                label = stringResource(R.string.label_dose_ml),
                supportingText = if (draft.enabled) {
                    stringResource(R.string.help_dose_required, doseMinLabel(), doseMaxLabel())
                } else {
                    stringResource(R.string.help_dose_preserved)
                },
                keyboardType = KeyboardType.Decimal,
                enabled = enabled,
                isError = mlInvalid,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AquaSpace.sm)) {
                ScheduleNumberField(
                    modifier = Modifier.weight(1f),
                    value = draft.hour,
                    onValueChange = onHourChanged,
                    label = stringResource(R.string.label_hour),
                    supportingText = stringResource(R.string.help_hour),
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    isError = hourInvalid,
                )
                ScheduleNumberField(
                    modifier = Modifier.weight(1f),
                    value = draft.minute,
                    onValueChange = onMinuteChanged,
                    label = stringResource(R.string.label_minute),
                    supportingText = stringResource(R.string.help_minute),
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    isError = minuteInvalid,
                )
                ScheduleNumberField(
                    modifier = Modifier.weight(1.35f),
                    value = draft.ml,
                    onValueChange = onMlChanged,
                    label = stringResource(R.string.label_dose_ml),
                    supportingText = if (draft.enabled) {
                        stringResource(R.string.help_dose_required, doseMinLabel(), doseMaxLabel())
                    } else {
                        stringResource(R.string.help_dose_preserved)
                    },
                    keyboardType = KeyboardType.Decimal,
                    enabled = enabled,
                    isError = mlInvalid,
                )
            }
        }
        if (hourInvalid || minuteInvalid || mlInvalid) {
            Spacer(Modifier.height(AquaSpace.sm))
            ErrorBanner(stringResource(R.string.error_schedule_slot_invalid, doseMinLabel(), doseMaxLabel()))
        }
        if (hasUnsavedChanges) {
            Spacer(Modifier.height(AquaSpace.sm))
            WarningBanner(stringResource(R.string.warning_schedule_unsaved))
        }
    }
}

@Composable
private fun ScheduleNumberField(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    isError: Boolean,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        enabled = enabled,
        isError = isError,
    )
}

@Composable
private fun LogsScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onManualRefresh: () -> Unit,
) {
    val logs = uiState.controllerStatus?.logs.orEmpty()
    ScreenList(contentPadding, widthClass) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_logs),
                subtitle = stringResource(R.string.logs_subtitle),
                status = stringResource(R.string.status_log_count, logs.size),
            )
        }
        item {
            SecondaryActionButton(
                text = if (uiState.isRefreshingStatus) stringResource(R.string.action_refreshing_logs) else stringResource(R.string.action_refresh_logs),
                icon = Icons.Filled.Refresh,
                onClick = onManualRefresh,
                enabled = !uiState.isRefreshingStatus,
            )
        }
        if (logs.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_no_logs_title),
                    body = stringResource(R.string.empty_no_logs_body),
                )
            }
        } else {
            itemsIndexed(logs) { index, line ->
                LogCard(index + 1, line)
            }
        }
        item { ErrorBanner(uiState.rawErrorDetails) }
    }
}

@Composable
private fun LogCard(index: Int, line: String) {
    Card(
        shape = AquaShape.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(AquaSpace.lg)) {
            Text(
                text = stringResource(R.string.log_title, index),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AquaSpace.xs))
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: MainUiState,
    widthClass: AquaWindowWidthClass,
    contentPadding: PaddingValues,
    onControllerAddressChanged: (String) -> Unit,
    onTimezoneInputChanged: (String) -> Unit,
    onSaveControllerAddress: () -> Unit,
    onClearSavedControllerAddress: () -> Unit,
    onSaveTimezone: () -> Unit,
    onResetWifiRequested: () -> Unit,
    onFactoryResetRequested: () -> Unit,
) {
    ScreenList(contentPadding, widthClass) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_settings),
                subtitle = stringResource(R.string.settings_subtitle),
                status = uiState.controllerAddress.ifBlank { stringResource(R.string.value_no_address_saved) },
            )
        }
        item {
            SectionCard(title = stringResource(R.string.section_controller_connection), subtitle = stringResource(R.string.settings_connection_subtitle)) {
                InfoRow(stringResource(R.string.label_current_address), uiState.controllerAddress.ifBlank { stringResource(R.string.value_no_address_saved) })
                Spacer(Modifier.height(AquaSpace.md))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.settingsControllerAddress,
                    onValueChange = onControllerAddressChanged,
                    label = { Text(stringResource(R.string.label_controller_address)) },
                    supportingText = { Text(stringResource(R.string.help_controller_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(AquaSpace.md))
                PrimaryActionButton(
                    text = stringResource(R.string.action_save_controller_address),
                    icon = Icons.Filled.Save,
                    onClick = onSaveControllerAddress,
                )
                Spacer(Modifier.height(AquaSpace.sm))
                SecondaryActionButton(
                    text = stringResource(R.string.action_clear_saved_address),
                    onClick = onClearSavedControllerAddress,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.section_timezone), subtitle = stringResource(R.string.timezone_subtitle)) {
                InfoRow(stringResource(R.string.label_controller_timezone), uiState.controllerStatus?.tz ?: stringResource(R.string.value_unknown))
                Spacer(Modifier.height(AquaSpace.md))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.timezoneInput,
                    onValueChange = onTimezoneInputChanged,
                    label = { Text(stringResource(R.string.label_timezone_rule)) },
                    supportingText = { Text(stringResource(R.string.help_timezone_rule)) },
                    singleLine = true,
                    enabled = !uiState.settingsActionInProgress,
                )
                Spacer(Modifier.height(AquaSpace.md))
                PrimaryActionButton(
                    text = if (uiState.settingsActionInProgress) stringResource(R.string.action_saving_timezone) else stringResource(R.string.action_save_timezone),
                    icon = Icons.Filled.Save,
                    loading = uiState.settingsActionInProgress,
                    onClick = onSaveTimezone,
                    enabled = !uiState.settingsActionInProgress,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.section_app_settings), subtitle = stringResource(R.string.app_settings_subtitle)) {
                InfoRow(stringResource(R.string.label_control_mode), stringResource(R.string.value_local_network))
                InfoRow(stringResource(R.string.label_data_storage), stringResource(R.string.value_saved_on_phone))
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.section_maintenance),
                subtitle = stringResource(R.string.maintenance_subtitle),
                tone = SectionTone.Warning,
            ) {
                WarningBanner(stringResource(R.string.warning_reset_wifi))
                Spacer(Modifier.height(AquaSpace.md))
                DangerOutlineButton(
                    text = stringResource(R.string.action_reset_wifi),
                    onClick = onResetWifiRequested,
                    enabled = !uiState.settingsActionInProgress,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.section_dangerous_actions),
                subtitle = stringResource(R.string.dangerous_actions_subtitle),
                tone = SectionTone.Danger,
            ) {
                DangerButton(
                    text = stringResource(R.string.action_factory_reset),
                    onClick = onFactoryResetRequested,
                    enabled = !uiState.settingsActionInProgress,
                )
            }
        }
        item { MessageCard(uiState.settingsMessage) }
    }
}

@Composable
private fun ScreenList(
    contentPadding: PaddingValues,
    widthClass: AquaWindowWidthClass = AquaWindowWidthClass.Compact,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = screenHorizontalPadding(widthClass),
            top = contentPadding.calculateTopPadding() + AquaSpace.lg,
            end = screenHorizontalPadding(widthClass),
            bottom = contentPadding.calculateBottomPadding() + AquaSpace.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(AquaSpace.lg),
        content = content,
    )
}

private fun screenHorizontalPadding(widthClass: AquaWindowWidthClass) =
    when (widthClass) {
        AquaWindowWidthClass.Compact -> AquaSpace.lg
        AquaWindowWidthClass.Medium -> AquaSpace.xl
        AquaWindowWidthClass.Expanded -> 32.dp
    }

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    status: String? = null,
    statusTone: StatusTone = StatusTone.Neutral,
) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(AquaSpace.xs))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!status.isNullOrBlank()) {
            Spacer(Modifier.height(AquaSpace.sm))
            StatusBadge(status, statusTone)
        }
    }
}

private enum class SectionTone { Normal, Selected, Warning, Danger }

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    tone: SectionTone = SectionTone.Normal,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = when (tone) {
        SectionTone.Normal -> MaterialTheme.colorScheme.surface
        SectionTone.Selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        SectionTone.Warning -> WarningContainer.copy(alpha = 0.56f)
        SectionTone.Danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
    }
    val contentColor = when (tone) {
        SectionTone.Normal -> MaterialTheme.colorScheme.onSurface
        SectionTone.Selected -> MaterialTheme.colorScheme.onPrimaryContainer
        SectionTone.Warning -> OnWarningContainer
        SectionTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AquaShape.card,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Column(Modifier.padding(AquaSpace.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(AquaSpace.xs))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.78f))
            }
            Spacer(Modifier.height(AquaSpace.lg))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaSpace.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class StatusTone { Success, Warning, Error, Neutral }

@Composable
private fun StatusBadge(text: String, tone: StatusTone) {
    val (container, content, icon) = when (tone) {
        StatusTone.Success -> Triple(SuccessContainer, OnSuccessContainer, Icons.Filled.CheckCircle)
        StatusTone.Warning -> Triple(WarningContainer, OnWarningContainer, Icons.Filled.Warning)
        StatusTone.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Error)
        StatusTone.Neutral -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Info)
    }
    Surface(
        shape = AquaShape.badge,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AquaSpace.md, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(AquaSpace.sm))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Banner(text = text, tone = StatusTone.Warning)
}

@Composable
private fun ErrorBanner(text: String) {
    if (text.isBlank()) return
    Banner(text = text, tone = StatusTone.Error)
}

@Composable
private fun Banner(text: String, tone: StatusTone) {
    val (container, content, icon) = when (tone) {
        StatusTone.Warning -> Triple(WarningContainer, OnWarningContainer, Icons.Filled.Warning)
        StatusTone.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Error)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Info)
    }
    Surface(shape = AquaShape.control, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AquaSpace.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AquaSpace.sm),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadingState(text: String) {
    Surface(shape = AquaShape.card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AquaSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AquaSpace.md),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Surface(shape = AquaShape.card, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AquaSpace.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(AquaSpace.xs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    if (message.isBlank()) return
    Surface(shape = AquaShape.control, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Text(modifier = Modifier.padding(AquaSpace.md), text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: ImageVector? = null,
    loading: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AquaShape.control,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(AquaSpace.sm))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AquaSpace.sm))
        }
        Text(text)
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AquaShape.control,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AquaSpace.sm))
        }
        Text(text)
    }
}

@Composable
private fun DangerButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AquaShape.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AquaSpace.sm))
        }
        Text(text)
    }
}

@Composable
private fun DangerOutlineButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AquaShape.control,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.62f)),
    ) {
        Text(text)
    }
}

@Composable
private fun BackTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(AquaSpace.sm))
        Text(text)
    }
}

@Composable
private fun EmergencyStopCard(uiState: MainUiState, onConfirmRequested: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.section_emergency_stop),
        subtitle = stringResource(R.string.emergency_stop_subtitle),
        tone = SectionTone.Danger,
    ) {
        DangerButton(
            text = if (uiState.emergencyStopInProgress) stringResource(R.string.action_stopping_pumps) else stringResource(R.string.action_stop_all_pumps),
            icon = Icons.Filled.Stop,
            onClick = onConfirmRequested,
            enabled = !uiState.emergencyStopInProgress,
        )
        if (uiState.emergencyStopInProgress) {
            Spacer(Modifier.height(AquaSpace.md))
            LoadingState(stringResource(R.string.loading_stopping_pumps))
        }
        if (uiState.emergencyStopMessage.isNotBlank()) {
            Spacer(Modifier.height(AquaSpace.md))
            Text(uiState.emergencyStopMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun connectionLabel(status: ConnectionStatus): String =
    when (status) {
        ConnectionStatus.NotConnected -> stringResource(R.string.connection_not_connected)
        ConnectionStatus.Connected -> stringResource(R.string.connection_online)
        ConnectionStatus.Disconnected -> stringResource(R.string.connection_offline)
    }

private fun connectionTone(status: ConnectionStatus): StatusTone =
    when (status) {
        ConnectionStatus.NotConnected -> StatusTone.Neutral
        ConnectionStatus.Connected -> StatusTone.Success
        ConnectionStatus.Disconnected -> StatusTone.Error
    }

private fun ScheduleSlotDraft.hasUnsavedChanges(): Boolean =
    enabled != savedEnabled ||
        hour != savedHour ||
        minute != savedMinute ||
        ml != savedMl

@Composable
private fun schedulePermissionText(allowed: Boolean): String =
    if (allowed) {
        stringResource(R.string.badge_scheduled_dosing_allowed)
    } else {
        stringResource(R.string.badge_schedules_blocked)
    }

@Composable
private fun schedulePermissionValue(allowed: Boolean): String =
    if (allowed) {
        stringResource(R.string.value_scheduled_dosing_allowed)
    } else {
        stringResource(R.string.value_schedules_blocked)
    }

@Composable
private fun pumpStateText(status: AquaDoseStatus): String {
    if (!status.running) return stringResource(R.string.pump_state_idle)
    val pump = status.pumps.getOrNull(status.runningPump)
    val name = pump?.name?.takeIf { it.isNotBlank() }
    val defaultReason = stringResource(R.string.pump_running_default_reason)
    val reason = status.runningReason.ifBlank { defaultReason }
    return if (name == null) {
        stringResource(R.string.pump_running_unnamed, status.runningPump + 1, reason)
    } else {
        stringResource(R.string.pump_running_named, status.runningPump + 1, name, reason)
    }
}

private fun manualDoseConfirm(
    uiState: MainUiState,
    onStartManualDose: () -> Unit,
    copy: ManualDoseDialogCopy,
): ConfirmAction {
    val ml = uiState.manualDoseMl.toDoubleOrNull()
    if (ml == null || AquaDoseValidation.validateMl(ml) != null) {
        return ConfirmAction(
            title = copy.invalidTitle,
            text = copy.invalidBody,
            confirmLabel = copy.okLabel,
            onConfirm = {},
        )
    }
    val body = if (ml > 20.0) {
        copy.largeBody.format(ml)
    } else {
        copy.normalBody.format(ml)
    }
    return ConfirmAction(
        title = if (ml > 20.0) copy.largeTitle else copy.normalTitle,
        text = body,
        confirmLabel = copy.confirmLabel,
        onConfirm = onStartManualDose,
        danger = ml > 20.0,
    )
}
