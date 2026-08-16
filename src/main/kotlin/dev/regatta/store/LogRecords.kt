package dev.regatta.store

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface LogRecord {
    val type: String
}

data class HeaderRecord(
    override val type: String = "header",
    val format: String = "regatta-jsonl",
    val version: Int = 1,
    val startedAt: Instant,
    val firmware: String? = null,
) : LogRecord

data class SessionStartRecord(
    override val type: String = "session_start",
    val timestamp: Instant,
    val workoutDistanceMeters: Int? = null,
    val workoutDurationSeconds: Long? = null,
) : LogRecord

data class SessionEndRecord(
    override val type: String = "session_end",
    val timestamp: Instant,
    val reason: String,
    val monitorDistanceMeters: Double? = null,
    val monitorKcalTotal: Double? = null,
) : LogRecord

data class ReadingRecord(
    override val type: String = "reading",
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
) : LogRecord

data class PacketRecord(
    override val type: String = "packet",
    val timestamp: Instant,
    val packet: String,
) : LogRecord
