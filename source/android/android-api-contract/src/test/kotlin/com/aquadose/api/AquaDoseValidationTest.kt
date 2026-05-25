package com.aquadose.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AquaDoseValidationTest {
    @Test
    fun validatesPumpIndexAgainstFirmwareRange() {
        assertNull(AquaDoseValidation.validatePumpIndex(0))
        assertNull(AquaDoseValidation.validatePumpIndex(7))
        assertNotNull(AquaDoseValidation.validatePumpIndex(-1))
        assertNotNull(AquaDoseValidation.validatePumpIndex(8))
    }

    @Test
    fun validatesPumpNameAgainstStoredFirmwareLength() {
        assertNull(AquaDoseValidation.validatePumpName("Alkalinity"))
        assertNull(AquaDoseValidation.validatePumpName("  Calcium  "))
        assertNotNull(AquaDoseValidation.validatePumpName(""))
        assertNotNull(AquaDoseValidation.validatePumpName("   "))
        assertNotNull(AquaDoseValidation.validatePumpName("12345678901234567890123456789012"))
    }

    @Test
    fun validatesMlValuesAgainstFirmwareRange() {
        assertNull(AquaDoseValidation.validateMl(0.1))
        assertNull(AquaDoseValidation.validateMl(1.0))
        assertNull(AquaDoseValidation.validateMl(7.0))
        assertNull(AquaDoseValidation.validateMl(25.0))
        assertNull(AquaDoseValidation.validateMl(100.0))
        assertNull(AquaDoseValidation.validateMl(250.5))
        assertNull(AquaDoseValidation.validateMl(999.0))
        assertNotNull(AquaDoseValidation.validateMl(0.0))
        assertNotNull(AquaDoseValidation.validateMl(0.09))
        assertNotNull(AquaDoseValidation.validateMl(999.1))
        assertNotNull(AquaDoseValidation.validateMl(1000.0))
        assertNotNull(AquaDoseValidation.validateMl(Double.NaN))
    }

    @Test
    fun validatesScheduleTime() {
        assertNull(AquaDoseValidation.validateScheduleHour(0))
        assertNull(AquaDoseValidation.validateScheduleHour(23))
        assertNotNull(AquaDoseValidation.validateScheduleHour(24))
        assertNull(AquaDoseValidation.validateScheduleMinute(0))
        assertNull(AquaDoseValidation.validateScheduleMinute(59))
        assertNotNull(AquaDoseValidation.validateScheduleMinute(60))
    }

    @Test
    fun validatesCalibrationValues() {
        assertNull(AquaDoseValidation.validateCalibrationGrams(1.0))
        assertNull(AquaDoseValidation.validateCalibrationGrams(6000.0))
        assertNotNull(AquaDoseValidation.validateCalibrationGrams(0.0))
        assertNotNull(AquaDoseValidation.validateCalibrationGrams(6000.1))

        assertNull(AquaDoseValidation.validateCalibrationRateMlPerSec(0.0))
        assertNull(AquaDoseValidation.validateCalibrationRateMlPerSec(100.0))
        assertNotNull(AquaDoseValidation.validateCalibrationRateMlPerSec(-0.1))
        assertNotNull(AquaDoseValidation.validateCalibrationRateMlPerSec(100.1))
    }

    @Test
    fun requiresFullScheduleSetForPumpConfig() {
        val update = PumpConfigUpdate(
            pumpIndex = 0,
            name = "Pump 1",
            enabled = true,
            mlPerSec = 1.0,
            schedules = List(7) {
                ScheduleConfigUpdate(enabled = false, hour = 12, minute = 0, ml = 1.0)
            },
        )

        val errors = AquaDoseValidation.validatePumpConfig(update)

        assertEquals(1, errors.count { it.field == "schedules" })
        assertTrue(errors.any { it.message.contains("Exactly 8") })
    }
}
