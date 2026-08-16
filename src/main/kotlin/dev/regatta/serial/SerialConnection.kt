package dev.regatta.serial

import java.nio.charset.StandardCharsets

interface SerialConnection : AutoCloseable {

    val isOpen: Boolean

    fun open()

    fun write(data: ByteArray)

    /** Blocking read up to the port's read timeout; 0 when no data is available. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun writeLine(text: String) {
        write((text + "\r\n").toByteArray(StandardCharsets.US_ASCII))
    }

    override fun close()
}
