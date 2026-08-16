package dev.regatta.serial

import dev.regatta.config.RegattaProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SerialPortProviderTest {

    private val provider = SerialPortProvider(RegattaProperties())

    @Test
    fun `accepts linux and windows candidate names`() {
        assertTrue(provider.isCandidate("/dev/ttyACM0"))
        assertTrue(provider.isCandidate("/dev/ttyACM1"))
        assertTrue(provider.isCandidate("/dev/ttyUSB0"))
        assertTrue(provider.isCandidate("COM4"))
        assertTrue(provider.isCandidate("COM12"))
    }

    @Test
    fun `rejects non candidate names`() {
        assertFalse(provider.isCandidate("/dev/ttyS0"))
        assertFalse(provider.isCandidate("/dev/ttyACMa"))
        assertFalse(provider.isCandidate("/dev/cu.usbserial"))
        assertFalse(provider.isCandidate("COM"))
        assertFalse(provider.isCandidate(""))
    }
}
