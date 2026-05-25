package com.aquadose.api

object AquaDoseFirmwareLimits {
    const val PUMP_COUNT: Int = 8
    const val SCHEDULE_SLOT_COUNT: Int = 8
    const val MAX_PUMP_NAME_LENGTH: Int = 31
    const val MIN_PUMP_INDEX: Int = 0
    const val MAX_PUMP_INDEX: Int = PUMP_COUNT - 1
    const val MIN_SCHEDULE_HOUR: Int = 0
    const val MAX_SCHEDULE_HOUR: Int = 23
    const val MIN_SCHEDULE_MINUTE: Int = 0
    const val MAX_SCHEDULE_MINUTE: Int = 59
    const val MIN_ML: Double = 0.1
    const val MAX_ML: Double = 999.0
    const val MAX_CALIBRATION_GRAMS: Double = 6000.0
    const val MAX_CALIBRATION_RATE_ML_PER_SEC: Double = 100.0
}

data class ValidationError(
    val field: String,
    val message: String,
)

object AquaDoseValidation {
    fun validatePumpName(name: String): ValidationError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationError("name", "Pump name must not be blank.")
            trimmed.length > AquaDoseFirmwareLimits.MAX_PUMP_NAME_LENGTH ->
                ValidationError("name", "Pump name must be 31 characters or fewer.")
            else -> null
        }
    }

    fun validatePumpIndex(pumpIndex: Int): ValidationError? =
        if (pumpIndex in AquaDoseFirmwareLimits.MIN_PUMP_INDEX..AquaDoseFirmwareLimits.MAX_PUMP_INDEX) {
            null
        } else {
            ValidationError("p", "Pump index must be 0 through 7.")
        }

    fun validateMl(ml: Double): ValidationError? =
        if (ml.isFinite() && ml >= AquaDoseFirmwareLimits.MIN_ML && ml <= AquaDoseFirmwareLimits.MAX_ML) {
            null
        } else {
            ValidationError("ml", "Milliliters must be between 0.1 and 999.")
        }

    fun validateScheduleHour(hour: Int): ValidationError? =
        if (hour in AquaDoseFirmwareLimits.MIN_SCHEDULE_HOUR..AquaDoseFirmwareLimits.MAX_SCHEDULE_HOUR) {
            null
        } else {
            ValidationError("hour", "Schedule hour must be 0 through 23.")
        }

    fun validateScheduleMinute(minute: Int): ValidationError? =
        if (minute in AquaDoseFirmwareLimits.MIN_SCHEDULE_MINUTE..AquaDoseFirmwareLimits.MAX_SCHEDULE_MINUTE) {
            null
        } else {
            ValidationError("minute", "Schedule minute must be 0 through 59.")
        }

    fun validateCalibrationGrams(grams: Double): ValidationError? =
        if (grams.isFinite() && grams > 0.0 && grams <= AquaDoseFirmwareLimits.MAX_CALIBRATION_GRAMS) {
            null
        } else {
            ValidationError("grams", "Calibration grams must be greater than 0 and at most 6000.")
        }

    fun validateCalibrationRateMlPerSec(rate: Double): ValidationError? =
        if (rate.isFinite() && rate >= 0.0 && rate <= AquaDoseFirmwareLimits.MAX_CALIBRATION_RATE_ML_PER_SEC) {
            null
        } else {
            ValidationError("rate", "Calibration rate must be 0 through 100 ml/sec.")
        }

    fun validatePumpConfig(update: PumpConfigUpdate): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        validatePumpIndex(update.pumpIndex)?.let(errors::add)
        validatePumpName(update.name)?.let(errors::add)
        validateCalibrationRateMlPerSec(update.mlPerSec)?.let(errors::add)
        if (update.schedules.size != AquaDoseFirmwareLimits.SCHEDULE_SLOT_COUNT) {
            errors += ValidationError("schedules", "Exactly 8 schedule slots must be sent to preserve firmware configuration.")
        }
        update.schedules.forEachIndexed { index, schedule ->
            validateScheduleHour(schedule.hour)?.let { errors += it.copy(field = "s${index}h") }
            validateScheduleMinute(schedule.minute)?.let { errors += it.copy(field = "s${index}m") }
            validateMl(schedule.ml)?.let { errors += it.copy(field = "s${index}ml") }
        }
        return errors
    }
}
