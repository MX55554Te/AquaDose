package com.aquadose.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AquaDoseStatus(
    @SerialName("running")
    val running: Boolean = false,
    @SerialName("runningPump")
    val runningPump: Int = -1,
    @SerialName("runningReason")
    val runningReason: String = "",
    @SerialName("tz")
    val tz: String = "",
    @SerialName("timeValid")
    val timeValid: Boolean = false,
    @SerialName("currentTime")
    val currentTime: String = "not synced",
    @SerialName("pumps")
    val pumps: List<AquaDosePump> = emptyList(),
    @SerialName("logs")
    val logs: List<String> = emptyList(),
)

@Serializable
data class AquaDosePump(
    @SerialName("id")
    val id: Int = -1,
    @SerialName("pin")
    val pin: Int = -1,
    @SerialName("name")
    val name: String = "",
    @SerialName("enabled")
    val enabled: Boolean = false,
    @SerialName("mlPerSec")
    val mlPerSec: Double = 0.0,
    @SerialName("schedules")
    val schedules: List<AquaDoseSchedule> = emptyList(),
)

@Serializable
data class AquaDoseSchedule(
    @SerialName("enabled")
    val enabled: Boolean = false,
    @SerialName("hour")
    val hour: Int = 12,
    @SerialName("minute")
    val minute: Int = 0,
    @SerialName("ml")
    val ml: Double = 1.0,
)

data class PumpConfigUpdate(
    val pumpIndex: Int,
    val name: String,
    val enabled: Boolean,
    val mlPerSec: Double,
    val schedules: List<ScheduleConfigUpdate>,
) {
    companion object {
        fun fromStatusPump(pump: AquaDosePump): PumpConfigUpdate =
            PumpConfigUpdate(
                pumpIndex = pump.id,
                name = pump.name,
                enabled = pump.enabled,
                mlPerSec = pump.mlPerSec,
                schedules = pump.schedules.map {
                    ScheduleConfigUpdate(
                        enabled = it.enabled,
                        hour = it.hour,
                        minute = it.minute,
                        ml = it.ml,
                    )
                },
            )
    }
}

data class ScheduleConfigUpdate(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val ml: Double,
)

