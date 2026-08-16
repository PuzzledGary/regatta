package dev.regatta.serial

import com.fazecast.jSerialComm.SerialPort
import dev.regatta.config.RegattaProperties

class JSerialCommConnection(
    private val port: SerialPort,
    private val config: RegattaProperties.Serial,
    private val readTimeoutMs: Int = 200,
) : SerialConnection {

    private var opened = false

    override val isOpen: Boolean
        get() = opened && port.isOpen

    override fun open() {
        port.setBaudRate(config.baudRate)
        port.setNumDataBits(config.dataBits)
        port.setNumStopBits(config.stopBits)
        port.setParity(config.parity)
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, readTimeoutMs, 0)
        check(port.openPort()) { "Failed to open serial port ${port.systemPortName}" }
        opened = true
    }

    override fun write(data: ByteArray) {
        check(isOpen) { "Serial port ${port.systemPortName} is not open" }
        val out = port.outputStream
        out.write(data)
        out.flush()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isOpen) return 0
        val n = port.inputStream.read(buffer, offset, length)
        return if (n < 0) 0 else n
    }

    override fun close() {
        if (opened) {
            port.closePort()
            opened = false
        }
    }
}
