package dev.regatta.capture

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadingBuilderTest {

    private val t = Instant.parse("2026-08-16T10:00:00Z")

    @Test
    fun `maps registers and derives elapsed pace watts and rate`() {
        val reading = ReadingBuilder.build(
            timestamp = t,
            distanceMeters = 0x19,
            clockTriple = 0x031710,
            clockHourMin = 0x0201,
            strokes = 8,
            strokeAverage = 500,
            speedCmPerSec = 200,
            kcalCurrent = 42,
            kcalTotal = 0x000848,
        )

        assertEquals(25.0, reading.distanceMeters)
        assertEquals(2 * 3600 + 3 * 60 + 23L, reading.elapsedSeconds)
        assertEquals(8L, reading.strokes)
        assertEquals(12.0, reading.strokeRate)
        assertEquals(2.0, reading.speedMps)
        assertEquals(250.0, reading.pacePer500mSeconds)
        val watts = reading.watts!!
        assertEquals(0.35, watts, 1e-9)
        assertEquals(42.0, reading.kcalCurrent)
        assertEquals(2120.0, reading.kcalTotal)
    }

    @Test
    fun `zero speed yields no derived pace or watts`() {
        val reading = ReadingBuilder.build(timestamp = t, speedCmPerSec = 0)

        assertEquals(0.0, reading.speedMps)
        assertNull(reading.pacePer500mSeconds)
        assertNull(reading.watts)
    }

    @Test
    fun `zero stroke average yields no rate`() {
        val reading = ReadingBuilder.build(timestamp = t, strokeAverage = 0)
        assertNull(reading.strokeRate)
    }

    @Test
    fun `null registers yield null fields`() {
        val reading = ReadingBuilder.build(timestamp = t)
        assertNull(reading.distanceMeters)
        assertNull(reading.elapsedSeconds)
        assertNull(reading.strokes)
        assertNull(reading.speedMps)
        assertNull(reading.kcalTotal)
    }
}
