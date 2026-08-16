package dev.regatta.protocol

import dev.regatta.serial.FakeSerialConnection
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S4ClientTest {

    private val shortTimeout = Duration.ofMillis(200)

    @Test
    fun `connect performs handshake and reads firmware`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)

        conn.feed("_WR_\r\nIV40210\r\n")
        val firmware = client.connect()

        assertEquals(Packet.Firmware(4, "2.10"), firmware)
        assertEquals(listOf("USB\r\n", "IV?\r\n"), conn.sent)
    }

    @Test
    fun `connect times out when the monitor never replies`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn, replyTimeout = shortTimeout)

        assertThrows(ProtocolException::class.java) { client.connect() }
    }

    @Test
    fun `readWord sends no-space command and returns the value`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("IDD0570019\r\n")
        assertEquals(25, client.readWord(0x057))
        assertEquals("IRD057\r\n", conn.sent.last())
    }

    @Test
    fun `readWord surfaces interleaved auto packets`() {
        val conn = FakeSerialConnection()
        val auto = mutableListOf<String>()
        val client = S4Client(conn, onAutoPacket = { auto += it })
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("SS\r\nP 12\r\nIDD0570019\r\n")
        assertEquals(25, client.readWord(0x057))
        assertEquals(listOf("SS", "P 12"), auto)
    }

    @Test
    fun `readWord retries on a mismatched address reply`() {
        val conn = FakeSerialConnection()
        val auto = mutableListOf<String>()
        val client = S4Client(conn, onAutoPacket = { auto += it })
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("IDD0580001\r\nIDD0570005\r\n")
        assertEquals(5, client.readWord(0x057))
        assertEquals(listOf("IDD0580001"), auto)
    }

    @Test
    fun `readTriple returns a 24-bit value`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("IDT1E0031710\r\n")
        assertEquals(0x031710, client.readTriple(0x1E0))
        assertEquals("IRT1E0\r\n", conn.sent.last())
    }

    @Test
    fun `error reply throws`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("ERROR\r\n")
        assertThrows(ProtocolException::class.java) { client.readWord(0x057) }
    }

    @Test
    fun `read times out awaiting the reply`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn, replyTimeout = shortTimeout)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        assertThrows(ProtocolException::class.java) { client.readWord(0x057) }
    }

    @Test
    fun `workout config packets await ok`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        conn.feed("OK\r\n")
        client.sendWorkoutDistance(unit = 1, target = 1000)
        assertEquals("WSI103E8\r\n", conn.sent.last())

        conn.feed("OK\r\n")
        client.sendWorkoutDuration(seconds = 300)
        assertEquals("WSU012C\r\n", conn.sent.last())
    }

    @Test
    fun `exit writes the exit packet`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        client.exit()
        assertEquals("EXIT\r\n", conn.sent.last())
    }

    @Test
    fun `close sends exit then closes the connection`() {
        val conn = FakeSerialConnection()
        val client = S4Client(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        client.connect()

        client.close()
        assertTrue(conn.closed)
        assertEquals("EXIT\r\n", conn.sent.last())
    }
}
