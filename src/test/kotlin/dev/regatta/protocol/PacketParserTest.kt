package dev.regatta.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PacketParserTest {

    @Test
    fun `parses control packets`() {
        assertEquals(Packet.Handshake, PacketParser.parse("_WR_"))
        assertEquals(Packet.Ok, PacketParser.parse("OK"))
        assertEquals(Packet.Error, PacketParser.parse("ERROR"))
        assertEquals(Packet.Ping, PacketParser.parse("PING"))
    }

    @Test
    fun `parses stroke packets`() {
        assertEquals(Packet.StrokeStart, PacketParser.parse("SS"))
        assertEquals(Packet.StrokeEnd, PacketParser.parse("SE"))
        assertEquals(Packet.Pulse(0x12), PacketParser.parse("P 12"))
    }

    @Test
    fun `accepts pulse packets without the space`() {
        assertEquals(Packet.Pulse(3), PacketParser.parse("P03"))
        assertEquals(Packet.Pulse(3), PacketParser.parse("P3"))
    }

    @Test
    fun `parses firmware as bcd version`() {
        assertEquals(Packet.Firmware(4, "2.10"), PacketParser.parse("IV40210"))
        assertEquals(Packet.Firmware(5, "2.00"), PacketParser.parse("IV50200"))
        assertEquals(Packet.Firmware(4, "2.01"), PacketParser.parse("IV40201"))
    }

    @Test
    fun `parses register replies with wire-order bytes`() {
        val single = PacketParser.parse("IDS0571A") as Packet.RegisterRead
        assertEquals(RegisterKind.SINGLE, single.kind)
        assertEquals(0x057, single.address)
        assertArrayEquals(byteArrayOf(0x1A.toByte()), single.bytes)
        assertEquals(0x1A, single.value)

        val word = PacketParser.parse("IDD0570019") as Packet.RegisterRead
        assertEquals(RegisterKind.DOUBLE, word.kind)
        assertEquals(0x057, word.address)
        assertArrayEquals(byteArrayOf(0x00, 0x19), word.bytes)
        assertEquals(0x19, word.value)

        val triple = PacketParser.parse("IDT1E0031710") as Packet.RegisterRead
        assertEquals(RegisterKind.TRIPLE, triple.kind)
        assertEquals(0x1E0, triple.address)
        assertArrayEquals(byteArrayOf(0x03, 0x17, 0x10), triple.bytes)
        assertEquals(0x031710, triple.value)
    }

    @Test
    fun `reports unknown lines`() {
        assertTrue(PacketParser.parse("FOO") is Packet.Unknown)
        assertTrue(PacketParser.parse("IDSZZZ12") is Packet.Unknown)
        assertTrue(PacketParser.parse("P") is Packet.Unknown)
        assertTrue(PacketParser.parse("") is Packet.Unknown)
        assertInstanceOf(Packet.Unknown::class.java, PacketParser.parse("USB"))
    }
}
