package com.aquadose.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AquaDoseFirmwareQueryTest {
    @Test
    fun changingOneSlotKeepsAllOtherScheduleValuesInSavePumpQuery() {
        val schedules = List(8) { index ->
            ScheduleConfigUpdate(
                enabled = index == 0,
                hour = 8 + index,
                minute = index,
                ml = 1.0 + index,
            )
        }.toMutableList()
        schedules[3] = schedules[3].copy(enabled = true, hour = 21, minute = 45, ml = 250.5)
        schedules[7] = schedules[7].copy(ml = 999.0)

        val query = AquaDoseFirmwareQuery.savePumpConfig(
            PumpConfigUpdate(
                pumpIndex = 0,
                name = "Pump 1",
                enabled = true,
                mlPerSec = 1.1167,
                schedules = schedules,
            ),
        )

        assertEquals("Pump 1", query["name"])
        assertEquals("1", query["enabled"])
        assertEquals("1.1167", query["rate"])
        assertEquals("1", query["s0en"])
        assertEquals("8", query["s0h"])
        assertEquals("0", query["s0m"])
        assertEquals("1", query["s0ml"])
        assertEquals("1", query["s3en"])
        assertEquals("21", query["s3h"])
        assertEquals("45", query["s3m"])
        assertEquals("250.5", query["s3ml"])
        assertEquals("0", query["s7en"])
        assertEquals("15", query["s7h"])
        assertEquals("7", query["s7m"])
        assertEquals("999", query["s7ml"])
        assertEquals(36, query.size)
    }

    @Test
    fun invalidHourMinuteAndEnabledMlAreRejectedByValidation() {
        val update = PumpConfigUpdate(
            pumpIndex = 0,
            name = "Pump 1",
            enabled = true,
            mlPerSec = 1.0,
            schedules = List(8) { index ->
                ScheduleConfigUpdate(
                    enabled = index == 2,
                    hour = if (index == 0) 24 else 12,
                    minute = if (index == 1) 60 else 0,
                    ml = if (index == 2) 0.0 else 1.0,
                )
            },
        )

        val errors = AquaDoseValidation.validatePumpConfig(update)

        assertTrue(errors.any { it.field == "s0h" })
        assertTrue(errors.any { it.field == "s1m" })
        assertTrue(errors.any { it.field == "s2ml" })
    }

    @Test
    fun disabledSlotCanBeRepresentedWithoutBeingEnabledInQuery() {
        val update = PumpConfigUpdate(
            pumpIndex = 0,
            name = "Pump 1",
            enabled = true,
            mlPerSec = 1.0,
            schedules = List(8) {
                ScheduleConfigUpdate(enabled = false, hour = 12, minute = 0, ml = 1.0)
            },
        )

        val query = AquaDoseFirmwareQuery.savePumpConfig(update)

        assertEquals("0", query["s0en"])
        assertFalse(query.values.any { it == "" })
    }
}
