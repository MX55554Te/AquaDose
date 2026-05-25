package com.aquadose.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AquaDoseModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun parsesStatusResponseUsingFirmwareFieldNames() {
        val raw = """
            {
              "running": false,
              "runningPump": -1,
              "runningReason": "",
              "tz": "IST-2IDT,M3.4.4/26,M10.5.0/25",
              "timeValid": true,
              "currentTime": "17:14:05",
              "pumps": [
                {
                  "id": 0,
                  "pin": 25,
                  "name": "Pump 1",
                  "enabled": true,
                  "mlPerSec": 1.1167,
                  "schedules": [
                    { "enabled": true, "hour": 14, "minute": 22, "ml": 50.00 }
                  ]
                }
              ],
              "logs": ["2026-05-13 16:57:01 | Time: NTP sync OK"]
            }
        """.trimIndent()

        val status = json.decodeFromString<AquaDoseStatus>(raw)

        assertFalse(status.running)
        assertEquals(-1, status.runningPump)
        assertEquals("IST-2IDT,M3.4.4/26,M10.5.0/25", status.tz)
        assertTrue(status.timeValid)
        assertEquals("17:14:05", status.currentTime)
        assertEquals(1, status.pumps.size)
        assertEquals(25, status.pumps.first().pin)
        assertEquals(1.1167, status.pumps.first().mlPerSec)
        assertEquals(14, status.pumps.first().schedules.first().hour)
        assertEquals(1, status.logs.size)
    }

    @Test
    fun missingLogsDefaultToEmptyList() {
        val raw = """
            {
              "running": false,
              "runningPump": -1,
              "runningReason": "",
              "tz": "UTC0",
              "timeValid": false,
              "currentTime": "not synced",
              "pumps": []
            }
        """.trimIndent()

        val status = json.decodeFromString<AquaDoseStatus>(raw)

        assertTrue(status.logs.isEmpty())
    }
}

