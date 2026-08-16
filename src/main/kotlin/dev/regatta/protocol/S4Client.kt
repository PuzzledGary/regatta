package dev.regatta.protocol

import dev.regatta.serial.CrlfFrameReader
import dev.regatta.serial.SerialConnection
import java.time.Duration

class S4Client(
    private val connection: SerialConnection,
    private val onAutoPacket: (String) -> Unit = {},
    private val replyTimeout: Duration = Duration.ofSeconds(5),
) {

    private val reader = CrlfFrameReader(connection)

    fun connect(): Packet.Firmware {
        connection.open()
        connection.writeLine("USB")
        await("handshake") { it is Packet.Handshake }
        return readFirmware()
    }

    fun readFirmware(): Packet.Firmware {
        connection.writeLine("IV?")
        return await("firmware") { it is Packet.Firmware } as Packet.Firmware
    }

    fun readByte(address: Int): Int = read(RegisterKind.SINGLE, address)

    fun readWord(address: Int): Int = read(RegisterKind.DOUBLE, address)

    fun readTriple(address: Int): Int = read(RegisterKind.TRIPLE, address)

    fun sendWorkoutDistance(unit: Int, target: Int) {
        connection.writeLine("WSI$unit${hex(target, 4)}")
        expectOk()
    }

    fun sendWorkoutDuration(seconds: Int) {
        connection.writeLine("WSU${hex(seconds, 4)}")
        expectOk()
    }

    fun exit() {
        connection.writeLine("EXIT")
    }

    fun close() {
        runCatching { exit() }
        connection.close()
    }

    private fun read(kind: RegisterKind, address: Int): Int {
        connection.writeLine(kind.command + hex(address, 3))
        val packet = await("reply ${kind.reply}${hex(address, 3)}") {
            it is Packet.RegisterRead && it.kind == kind && it.address == address
        }
        return (packet as Packet.RegisterRead).value
    }

    private fun expectOk() {
        await("OK") { it is Packet.Ok }
    }

    private fun await(expected: String, matches: (Packet) -> Boolean): Packet {
        val deadline = System.nanoTime() + replyTimeout.toNanos()
        while (System.nanoTime() < deadline) {
            val frame = reader.nextFrame() ?: continue
            val packet = PacketParser.parse(frame)
            if (matches(packet)) return packet
            if (packet is Packet.Error) throw ProtocolException("S4 replied ERROR while awaiting $expected")
            onAutoPacket(frame)
        }
        throw ProtocolException("Timed out after ${replyTimeout.toMillis()}ms waiting for $expected")
    }

    private fun hex(value: Int, digits: Int): String =
        value.toString(16).uppercase().padStart(digits, '0')
}
