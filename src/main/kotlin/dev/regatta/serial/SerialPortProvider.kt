package dev.regatta.serial

import com.fazecast.jSerialComm.SerialPort
import dev.regatta.config.RegattaProperties

class SerialPortProvider(
    private val properties: RegattaProperties,
) {

    fun detect(): SerialPort? {
        properties.serial.device?.takeIf { it.isNotBlank() }?.let { device ->
            return SerialPort.getCommPort(device)
        }
        return SerialPort.getCommPorts().firstOrNull { isCandidate(it.systemPortName) }
    }

    internal fun isCandidate(name: String): Boolean =
        name.matches(CANDIDATE)

    companion object {
        private val CANDIDATE = Regex("/dev/ttyUSB\\d+|/dev/ttyACM\\d+|COM\\d+")
    }
}
