package dev.regatta.capture

import dev.regatta.config.RegattaProperties
import dev.regatta.protocol.PacketParser
import dev.regatta.session.Reading
import dev.regatta.session.SessionEndReason
import dev.regatta.session.SessionManager
import dev.regatta.session.SessionStatus
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "regatta.demo", name = ["enabled"], havingValue = "false")
class CaptureService(
    private val device: CaptureDevice,
    properties: RegattaProperties,
) {

    private val session = SessionManager(
        idleTimeout = properties.session.idleTimeout,
        workoutDistanceMeters = properties.session.workoutDistanceMeters,
        workoutDurationSeconds = properties.session.workoutDurationSeconds,
        onTransition = ::onTransition,
    )

    private var connected = false

    @PostConstruct
    fun start() {
        device.onPacket = ::onPacket
        try {
            val firmware = device.connect()
            connected = true
            println("capture: connected, firmware ${firmware.series} — ${firmware.version}")
            session.start()
        } catch (e: Exception) {
            println("capture: connect failed: ${e.message}")
        }
    }

    @PreDestroy
    fun shutdown() {
        device.disconnect()
    }

    @Scheduled(fixedDelayString = "\${regatta.session.poll-interval}")
    fun poll() {
        if (!connected) return
        val reading = device.poll()
        if (reading == null) {
            connected = false
            session.disconnect()
            println("capture: poll failed or device disconnected")
            return
        }
        session.observeReading(reading)
        println(formatReading(reading))
    }

    private fun onPacket(line: String) {
        try {
            session.observePacket(PacketParser.parse(line))
        } catch (_: Exception) {
            // ignore unparseable frames
        }
    }

    private fun onTransition(status: SessionStatus, reason: SessionEndReason?) {
        val suffix = reason?.let { " — reason=$it" } ?: ""
        println("capture: status -> $status$suffix")
    }

    private fun formatReading(r: Reading): String {
        val fmt = { v: Double?, suffix: String ->
            if (v == null) "?" + suffix else String.format("%.1f%s", v, suffix)
        }
        val elapsed = r.elapsedSeconds?.let { sec ->
            "%02d:%02d".format(sec / 60, sec % 60)
        } ?: "??:??"
        val pace = r.pacePer500mSeconds?.let { s ->
            "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)
        } ?: "--:--"
        return "capture: dist=${r.distanceMeters ?: "?"}m  t=$elapsed  strokes=${r.strokes ?: "?"}" +
            "  spm=${fmt(r.strokeRate, "")}  pace=$pace /500m  ${fmt(r.speedMps, "m/s")}" +
            "  watts=${fmt(r.watts, "")}  kcal=${fmt(r.kcalTotal, "")}"
    }
}
