package dev.regatta.protocol

sealed interface Packet {

    data object StrokeStart : Packet

    data object StrokeEnd : Packet

    data class Pulse(val count: Int) : Packet

    data object Ok : Packet

    data object Error : Packet

    data object Ping : Packet

    data object Handshake : Packet

    data class Firmware(val series: Int, val version: String) : Packet

    data class RegisterRead(
        val kind: RegisterKind,
        val address: Int,
        val bytes: ByteArray,
        val value: Int,
    ) : Packet

    data class Unknown(val line: String) : Packet
}

enum class RegisterKind(val command: String, val reply: String) {
    SINGLE("IRS", "IDS"),
    DOUBLE("IRD", "IDD"),
    TRIPLE("IRT", "IDT"),
}
