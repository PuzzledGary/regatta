package dev.regatta.capture

import dev.regatta.protocol.Packet
import dev.regatta.session.Reading

interface CaptureDevice {

    var onPacket: (String) -> Unit

    fun connect(): Packet.Firmware

    fun poll(): Reading?

    fun disconnect()
}
