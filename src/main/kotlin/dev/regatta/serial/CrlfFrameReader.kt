package dev.regatta.serial

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory

class CrlfFrameReader(
    private val connection: SerialConnection,
    private val maxLineBytes: Int = MAX_LINE_BYTES,
) {

    private val buffer = ByteArrayOutputStream()
    private val log = LoggerFactory.getLogger(CrlfFrameReader::class.java)

    /** One blocking read; returns the first complete CR LF frame, or null. */
    fun nextFrame(): String? {
        val chunk = ByteArray(256)
        val n = connection.read(chunk, 0, chunk.size)
        if (n > 0) buffer.write(chunk, 0, n)
        return extract()
    }

    private fun extract(): String? {
        while (true) {
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) return null
            val terminator = findCrlf(bytes, capEnd = minOf(bytes.size - 1, maxLineBytes + 2))
            if (terminator >= 0) {
                val frame = String(bytes, 0, terminator, StandardCharsets.US_ASCII)
                buffer.reset()
                if (terminator + 2 < bytes.size) buffer.write(bytes, terminator + 2, bytes.size - terminator - 2)
                return frame
            }
            if (bytes.size <= maxLineBytes + 2) return null
            log.warn("Dropping {} bytes of non-frame data (no CR LF within {} bytes)", bytes.size, maxLineBytes)
            val resync = findCrlf(bytes, capEnd = bytes.size - 1)
            buffer.reset()
            if (resync >= 0) buffer.write(bytes, resync + 2, bytes.size - resync - 2)
        }
    }

    private fun findCrlf(bytes: ByteArray, capEnd: Int): Int {
        var i = 0
        while (i < capEnd) {
            if (bytes[i] == CR && bytes[i + 1] == LF) return i
            i++
        }
        return -1
    }

    companion object {
        const val CR: Byte = 0x0d
        const val LF: Byte = 0x0a
        const val MAX_LINE_BYTES = 50
    }
}
