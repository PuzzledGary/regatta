package dev.regatta.protocol

object PacketParser {

    private val REGISTER = listOf(
        RegisterKind.SINGLE to Regex("IDS([0-9A-F]{3})([0-9A-F]{2})"),
        RegisterKind.DOUBLE to Regex("IDD([0-9A-F]{3})([0-9A-F]{4})"),
        RegisterKind.TRIPLE to Regex("IDT([0-9A-F]{3})([0-9A-F]{6})"),
    )
    private val PULSE = Regex("P\\s?([0-9A-F]{1,2})")
    private val FIRMWARE = Regex("IV([45])([0-9A-F]{2})([0-9A-F]{2})")

    fun parse(line: String): Packet = when (line) {
        "_WR_" -> Packet.Handshake
        "SS" -> Packet.StrokeStart
        "SE" -> Packet.StrokeEnd
        "OK" -> Packet.Ok
        "ERROR" -> Packet.Error
        "PING" -> Packet.Ping
        else -> parsePulse(line) ?: parseFirmware(line) ?: parseRegister(line) ?: Packet.Unknown(line)
    }

    private fun parsePulse(line: String): Packet.Pulse? {
        val m = PULSE.matchEntire(line) ?: return null
        return Packet.Pulse(m.groupValues[1].toInt(16))
    }

    private fun parseFirmware(line: String): Packet.Firmware? {
        val m = FIRMWARE.matchEntire(line) ?: return null
        val series = m.groupValues[1].toInt()
        val version = "${bcd(m.groupValues[2].toInt(16))}.${bcd(m.groupValues[3].toInt(16)).toString().padStart(2, '0')}"
        return Packet.Firmware(series, version)
    }

    private fun parseRegister(line: String): Packet.RegisterRead? {
        for ((kind, regex) in REGISTER) {
            val m = regex.matchEntire(line) ?: continue
            val address = m.groupValues[1].toInt(16)
            val bytes = hexBytes(m.groupValues[2])
            val value = bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
            return Packet.RegisterRead(kind, address, bytes, value)
        }
        return null
    }

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun bcd(byte: Int): Int = ((byte and 0xf0) ushr 4) * 10 + (byte and 0x0f)
}
