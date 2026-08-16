package dev.regatta.capture

import dev.regatta.config.RegattaProperties
import dev.regatta.protocol.Packet
import dev.regatta.protocol.S4Client
import dev.regatta.serial.JSerialCommConnection
import dev.regatta.serial.SerialConnection
import dev.regatta.serial.SerialPortProvider
import dev.regatta.session.Reading
import java.time.Duration
import java.time.Instant

class S4CaptureDevice(
    properties: RegattaProperties,
    private val minPacketSpacingMs: Long = 25,
    private val connectionSupplier: () -> SerialConnection = {
        val port = SerialPortProvider(properties).detect()
            ?: throw IllegalStateException("No S4 serial port detected")
        JSerialCommConnection(port, properties.serial)
    },
) : CaptureDevice {

    override var onPacket: (String) -> Unit = {}

    private var client: S4Client? = null
    private var lastCommandAt: Instant? = null

    override fun connect(): Packet.Firmware {
        val s4 = S4Client(connectionSupplier(), onAutoPacket = { onPacket(it) })
        val firmware = s4.connect()
        client = s4
        return firmware
    }

    override fun poll(): Reading? {
        val s4 = client ?: return null
        return try {
            pace()
            val distance = s4.readWord(0x057)
            pace()
            val clock = s4.readTriple(0x1E0)
            pace()
            val clockHourMin = s4.readWord(0x1E2)
            pace()
            val strokes = s4.readWord(0x140)
            pace()
            val strokeAverage = s4.readWord(0x142)
            pace()
            val speed = s4.readWord(0x14A)
            pace()
            val kcalCurrent = s4.readWord(0x088)
            pace()
            val kcalTotal = s4.readTriple(0x08A)
            ReadingBuilder.build(
                timestamp = Instant.now(),
                distanceMeters = distance,
                clockTriple = clock,
                clockHourMin = clockHourMin,
                strokes = strokes,
                strokeAverage = strokeAverage,
                speedCmPerSec = speed,
                kcalCurrent = kcalCurrent,
                kcalTotal = kcalTotal,
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun disconnect() {
        client?.close()
        client = null
        lastCommandAt = null
    }

    private fun pace() {
        val last = lastCommandAt
        if (last != null) {
            val waitMs = minPacketSpacingMs - Duration.between(last, Instant.now()).toMillis()
            if (waitMs > 0) Thread.sleep(waitMs)
        }
        lastCommandAt = Instant.now()
    }
}
