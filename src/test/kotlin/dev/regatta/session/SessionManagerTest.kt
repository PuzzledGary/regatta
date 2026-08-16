package dev.regatta.session

import dev.regatta.protocol.Packet
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionManagerTest {

    private val idleTimeout = Duration.ofSeconds(5)
    private val t = Instant.parse("2026-08-16T10:00:00Z")

    @Test
    fun `start moves to connecting`() {
        val fx = fixture()
        fx.sm.start()
        assertEquals(SessionStatus.CONNECTING, fx.sm.status)
        assertEquals(listOf<Pair<SessionStatus, SessionEndReason?>>(SessionStatus.CONNECTING to null), fx.transitions)
    }

    @Test
    fun `stale idle data never starts a session`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observeReading(reading(distance = 61.0, strokes = 8, elapsed = 56))
        fx.sm.observeReading(reading(distance = 61.0, strokes = 8, elapsed = 56))
        fx.sm.observeReading(reading(distance = 61.0, strokes = 8, elapsed = 56))

        assertEquals(SessionStatus.CONNECTING, fx.sm.status)
    }

    @Test
    fun `distance increase starts the session`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observeReading(reading(distance = 0.0))
        fx.sm.observeReading(reading(distance = 1.5))

        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    @Test
    fun `attach mid workout starts on second rising poll`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observeReading(reading(distance = 500.0))
        fx.sm.observeReading(reading(distance = 505.0))

        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    @Test
    fun `strokes increase starts the session`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observeReading(reading(strokes = 8))
        fx.sm.observeReading(reading(strokes = 9))

        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    @Test
    fun `ss packet starts the session even with static registers`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observePacket(Packet.StrokeStart)
        fx.sm.observeReading(reading(distance = 1.0))

        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    @Test
    fun `user stop ends an active session with USER_STOP`() {
        val fx = fixture()
        activate(fx)

        fx.sm.stop()

        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.USER_STOP, fx.sm.endReason)
    }

    @Test
    fun `user stop during connecting returns to idle without a session`() {
        val fx = fixture()
        fx.sm.start()

        fx.sm.stop()

        assertEquals(SessionStatus.IDLE, fx.sm.status)
        assertNull(fx.sm.endReason)
    }

    @Test
    fun `distance target reached ends the session`() {
        val fx = fixture(workoutDistanceMeters = 1000)
        activate(fx, distance = 900.0)

        fx.sm.observeReading(reading(distance = 1000.0, t = t.plusSeconds(2)))

        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.TARGET_REACHED, fx.sm.endReason)
    }

    @Test
    fun `duration target reached ends the session`() {
        val fx = fixture(workoutDurationSeconds = 300)
        activate(fx, elapsed = 280)

        fx.sm.observeReading(reading(elapsed = 301, t = t.plusSeconds(2)))

        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.TARGET_REACHED, fx.sm.endReason)
    }

    @Test
    fun `register drop ends the session with RESET`() {
        val fx = fixture()
        activate(fx, distance = 100.0)

        fx.sm.observeReading(reading(distance = 0.0, t = t.plusSeconds(2)))

        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.RESET, fx.sm.endReason)
    }

    @Test
    fun `register drop during connecting just re-baselines`() {
        val fx = fixture()
        fx.sm.start()
        fx.sm.observeReading(reading(distance = 61.0))
        fx.sm.observeReading(reading(distance = 0.0))

        assertEquals(SessionStatus.CONNECTING, fx.sm.status)
    }

    @Test
    fun `idle after timeout ends the session`() {
        val fx = fixture()
        activate(fx)

        fx.sm.observeReading(reading(distance = 100.0, t = t.plusSeconds(4)))
        assertEquals(SessionStatus.ACTIVE, fx.sm.status)

        fx.sm.observeReading(reading(distance = 100.0, t = t.plusSeconds(9)))
        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.IDLE, fx.sm.endReason)
    }

    @Test
    fun `activity refreshes the idle timer`() {
        val fx = fixture()
        activate(fx)

        fx.sm.observeReading(reading(distance = 100.0, t = t.plusSeconds(4)))
        fx.sm.observeReading(reading(distance = 105.0, t = t.plusSeconds(4)))
        fx.sm.observeReading(reading(distance = 105.0, t = t.plusSeconds(8)))
        assertEquals(SessionStatus.ACTIVE, fx.sm.status)

        fx.sm.observeReading(reading(distance = 105.0, t = t.plusSeconds(9)))
        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.IDLE, fx.sm.endReason)
    }

    @Test
    fun `disconnect ends an active session with DISCONNECT`() {
        val fx = fixture()
        activate(fx)

        fx.sm.disconnect()

        assertEquals(SessionStatus.ENDED, fx.sm.status)
        assertEquals(SessionEndReason.DISCONNECT, fx.sm.endReason)
    }

    @Test
    fun `disconnect during connecting returns to idle`() {
        val fx = fixture()
        fx.sm.start()

        fx.sm.disconnect()

        assertEquals(SessionStatus.IDLE, fx.sm.status)
        assertNull(fx.sm.endReason)
    }

    @Test
    fun `start from ended restarts a new session`() {
        val fx = fixture()
        activate(fx)
        fx.sm.stop()

        fx.sm.start()
        fx.sm.observeReading(reading(distance = 0.0))
        fx.sm.observeReading(reading(distance = 2.0))

        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    private fun activate(fx: Fixture, distance: Double = 10.0, elapsed: Long = 0) {
        fx.sm.start()
        fx.sm.observeReading(reading(distance = 0.0, elapsed = 0, t = t))
        fx.sm.observeReading(reading(distance = distance, elapsed = elapsed, t = t.plusSeconds(1)))
        assertEquals(SessionStatus.ACTIVE, fx.sm.status)
    }

    private fun fixture(
        idleTimeout: Duration = this.idleTimeout,
        workoutDistanceMeters: Int? = null,
        workoutDurationSeconds: Long? = null,
    ): Fixture {
        val transitions = mutableListOf<Pair<SessionStatus, SessionEndReason?>>()
        val sm = SessionManager(
            idleTimeout = idleTimeout,
            workoutDistanceMeters = workoutDistanceMeters,
            workoutDurationSeconds = workoutDurationSeconds,
            onTransition = { status, reason -> transitions += status to reason },
        )
        return Fixture(sm, transitions)
    }

    private fun reading(distance: Double? = null, strokes: Long? = null, elapsed: Long? = null, t: Instant = this.t) =
        Reading(timestamp = t, distanceMeters = distance, strokes = strokes, elapsedSeconds = elapsed)

    private class Fixture(val sm: SessionManager, val transitions: MutableList<Pair<SessionStatus, SessionEndReason?>>)
}
