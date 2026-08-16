package dev.regatta.capture

import dev.regatta.config.RegattaProperties
import dev.regatta.protocol.Packet
import dev.regatta.serial.FakeSerialConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class S4CaptureDeviceTest {

    @Test
    fun `connect handshakes and reads firmware`() {
        val conn = FakeSerialConnection()
        val device = deviceWith(conn)
        conn.feed("_WR_\r\nIV40210\r\n")

        val firmware = device.connect()

        assertEquals(Packet.Firmware(4, "2.10"), firmware)
        assertEquals(listOf("USB\r\n", "IV?\r\n"), conn.sent)
    }

    @Test
    fun `poll reads all registers and builds a reading`() {
        val conn = FakeSerialConnection()
        val device = deviceWith(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        device.connect()

        conn.feed(
            "IDD0570019\r\n" + // distance 25
                "IDT1E0031710\r\n" + // min 3, sec 23, decs 0x10
                "IDD1E20201\r\n" + // hr 2
                "IDD1400008\r\n" + // strokes 8
                "IDD14201F4\r\n" + // stroke avg 500 -> 12 spm
                "IDD14A00C8\r\n" + // speed 200 cm/s
                "IDD088002A\r\n" + // kcal current 42
                "IDT08A000848\r\n", // total kcal 2120
        )
        val reading = device.poll()!!

        assertEquals(25.0, reading.distanceMeters)
        assertEquals(2 * 3600 + 3 * 60 + 23L, reading.elapsedSeconds)
        assertEquals(8L, reading.strokes)
        assertEquals(12.0, reading.strokeRate)
        assertEquals(2.0, reading.speedMps)
        assertEquals(250.0, reading.pacePer500mSeconds)
        assertEquals(42.0, reading.kcalCurrent)
        assertEquals(2120.0, reading.kcalTotal)
        assertEquals(
            listOf(
                "IRD057\r\n",
                "IRT1E0\r\n",
                "IRD1E2\r\n",
                "IRD140\r\n",
                "IRD142\r\n",
                "IRD14A\r\n",
                "IRD088\r\n",
                "IRT08A\r\n",
            ),
            conn.sent.drop(2),
        )
    }

    @Test
    fun `routes auto packets to the listener`() {
        val conn = FakeSerialConnection()
        val device = deviceWith(conn)
        val packets = mutableListOf<String>()
        device.onPacket = { packets += it }
        conn.feed("_WR_\r\nIV40210\r\n")
        device.connect()

        conn.feed("SS\r\nIDD057000A\r\n")
        device.poll()

        assertEquals(listOf("SS"), packets)
    }

    @Test
    fun `poll returns null when the monitor errors`() {
        val conn = FakeSerialConnection()
        val device = deviceWith(conn)
        conn.feed("_WR_\r\nIV40210\r\n")
        device.connect()

        conn.feed("ERROR\r\n")
        assertNull(device.poll())
    }

    @Test
    fun `poll returns null when not connected`() {
        assertNull(S4CaptureDevice(RegattaProperties(), minPacketSpacingMs = 0).poll())
    }

    @Test
    fun `connect fails when no connection can be opened`() {
        val device = S4CaptureDevice(
            RegattaProperties(),
            minPacketSpacingMs = 0,
            connectionSupplier = { throw IllegalStateException("No S4 serial port detected") },
        )
        val e = assertThrows(IllegalStateException::class.java) { device.connect() }
        assertEquals("No S4 serial port detected", e.message)
    }

    private fun deviceWith(conn: FakeSerialConnection): S4CaptureDevice =
        S4CaptureDevice(
            RegattaProperties(),
            minPacketSpacingMs = 0,
            connectionSupplier = { conn },
        )
}
