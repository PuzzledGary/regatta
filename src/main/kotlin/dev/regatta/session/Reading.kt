package dev.regatta.session

import java.time.Instant

data class Reading(
    val timestamp: Instant,
    val distanceMeters: Double? = null,
    val elapsedSeconds: Long? = null,
    val strokes: Long? = null,
    val strokeRate: Double? = null,
    val speedMps: Double? = null,
    val pacePer500mSeconds: Double? = null,
    val watts: Double? = null,
    val kcalCurrent: Double? = null,
    val kcalTotal: Double? = null,
)
