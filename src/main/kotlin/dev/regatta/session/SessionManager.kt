package dev.regatta.session

import dev.regatta.protocol.Packet
import java.time.Duration
import java.time.Instant

class SessionManager(
    private val idleTimeout: Duration,
    private val workoutDistanceMeters: Int? = null,
    private val workoutDurationSeconds: Long? = null,
    private val onTransition: (SessionStatus, SessionEndReason?) -> Unit = { _, _ -> },
) {

    @Volatile
    var status: SessionStatus = SessionStatus.IDLE
        private set

    @Volatile
    var endReason: SessionEndReason? = null
        private set

    private var previous: Reading? = null
    private var lastActivityAt: Instant? = null
    private var pendingPacketActivity = false

    @Synchronized
    fun start() {
        if (status == SessionStatus.ACTIVE) return
        resetObservationState()
        transitionTo(SessionStatus.CONNECTING, null)
    }

    @Synchronized
    fun observePacket(packet: Packet) {
        when (packet) {
            is Packet.StrokeStart, is Packet.StrokeEnd -> pendingPacketActivity = true
            else -> {}
        }
    }

    @Synchronized
    fun observeReading(reading: Reading) {
        val previous = this.previous
        val evidence = pendingPacketActivity || (previous != null && increased(reading, previous))
        when (status) {
            SessionStatus.CONNECTING -> {
                if (evidence) {
                    lastActivityAt = reading.timestamp
                    transitionTo(SessionStatus.ACTIVE, null)
                }
            }
            SessionStatus.ACTIVE -> {
                when {
                    targetReached(reading) -> end(SessionEndReason.TARGET_REACHED)
                    previous != null && decreased(reading, previous) -> end(SessionEndReason.RESET)
                    evidence -> lastActivityAt = reading.timestamp
                    lastActivityAt != null && Duration.between(lastActivityAt, reading.timestamp) >= idleTimeout ->
                        end(SessionEndReason.IDLE)
                }
            }
            else -> {}
        }
        pendingPacketActivity = false
        this.previous = reading
    }

    @Synchronized
    fun stop() {
        when (status) {
            SessionStatus.ACTIVE -> end(SessionEndReason.USER_STOP)
            SessionStatus.CONNECTING -> backToIdle()
            else -> {}
        }
    }

    @Synchronized
    fun disconnect() {
        when (status) {
            SessionStatus.ACTIVE -> end(SessionEndReason.DISCONNECT)
            SessionStatus.CONNECTING -> backToIdle()
            else -> {}
        }
    }

    private fun resetObservationState() {
        endReason = null
        previous = null
        lastActivityAt = null
        pendingPacketActivity = false
    }

    private fun backToIdle() {
        resetObservationState()
        transitionTo(SessionStatus.IDLE, null)
    }

    private fun end(reason: SessionEndReason) {
        endReason = reason
        transitionTo(SessionStatus.ENDED, reason)
    }

    private fun transitionTo(status: SessionStatus, reason: SessionEndReason?) {
        this.status = status
        onTransition(status, reason)
    }

    private fun increased(current: Reading, previous: Reading): Boolean =
        grew(current.distanceMeters, previous.distanceMeters) ||
            grew(current.strokes, previous.strokes) ||
            grew(current.elapsedSeconds, previous.elapsedSeconds)

    private fun decreased(current: Reading, previous: Reading): Boolean =
        shrank(current.distanceMeters, previous.distanceMeters) ||
            shrank(current.strokes, previous.strokes) ||
            shrank(current.elapsedSeconds, previous.elapsedSeconds)

    private fun targetReached(reading: Reading): Boolean {
        workoutDistanceMeters?.let { if (reading.distanceMeters != null && reading.distanceMeters >= it) return true }
        workoutDurationSeconds?.let { if (reading.elapsedSeconds != null && reading.elapsedSeconds >= it) return true }
        return false
    }

    private fun <T : Comparable<T>> grew(current: T?, previous: T?): Boolean =
        current != null && previous != null && current.compareTo(previous) > 0

    private fun <T : Comparable<T>> shrank(current: T?, previous: T?): Boolean =
        current != null && previous != null && current.compareTo(previous) < 0
}
