package dev.regatta.capture

import dev.regatta.session.Reading
import java.time.Instant

object ReadingBuilder {

    fun build(
        timestamp: Instant,
        distanceMeters: Int? = null,
        clockTriple: Int? = null,
        clockHourMin: Int? = null,
        strokes: Int? = null,
        strokeAverage: Int? = null,
        speedCmPerSec: Int? = null,
        kcalCurrent: Int? = null,
        kcalTotal: Int? = null,
    ): Reading {
        val elapsed = elapsedSeconds(clockTriple, clockHourMin)
        val speedMps = speedCmPerSec?.let { it / 100.0 }
        return Reading(
            timestamp = timestamp,
            distanceMeters = distanceMeters?.toDouble(),
            elapsedSeconds = elapsed,
            strokes = strokes?.toLong(),
            strokeRate = strokeAverage?.takeIf { it > 0 }?.let { 6000.0 / it },
            speedMps = speedMps,
            pacePer500mSeconds = speedMps?.takeIf { it > 0 }?.let { 500.0 / it },
            watts = speedMps?.takeIf { it > 0 }?.let { 2.8 / (it * it * it) },
            kcalCurrent = kcalCurrent?.toDouble(),
            kcalTotal = kcalTotal?.toDouble(),
        )
    }

    private fun elapsedSeconds(clockTriple: Int?, clockHourMin: Int?): Long? {
        val hours = (clockHourMin?.shr(8))?.and(0xff)?.toLong()
        val minutes = (clockTriple?.shr(16))?.and(0xff)?.toLong()
        val seconds = (clockTriple?.shr(8))?.and(0xff)?.toLong()
        return if (hours != null && minutes != null && seconds != null) {
            hours * 3600 + minutes * 60 + seconds
        } else null
    }
}
