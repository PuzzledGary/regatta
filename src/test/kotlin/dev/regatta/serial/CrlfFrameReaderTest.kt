package dev.regatta.serial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CrlfFrameReaderTest {

    @Test
    fun `reads a full frame in one read`() {
        val conn = FakeConnection("USB\r\n")
        val reader = CrlfFrameReader(conn)

        assertEquals("USB", reader.nextFrame())
    }

    @Test
    fun `returns null on timeout with no data`() {
        val conn = FakeConnection()
        val reader = CrlfFrameReader(conn)

        assertNull(reader.nextFrame())
    }

    @Test
    fun `assembles a frame split across reads`() {
        val conn = FakeConnection("USB\r")
        val reader = CrlfFrameReader(conn)

        assertNull(reader.nextFrame())
        conn.push("\n")
        assertEquals("USB", reader.nextFrame())
    }

    @Test
    fun `reads many frames from one burst`() {
        val conn = FakeConnection("SS\r\nSE\r\nP 12\r\n")
        val reader = CrlfFrameReader(conn)

        assertEquals("SS", reader.nextFrame())
        assertEquals("SE", reader.nextFrame())
        assertEquals("P 12", reader.nextFrame())
        assertNull(reader.nextFrame())
    }

    @Test
    fun `drains buffered frames even when the read times out`() {
        val conn = FakeConnection("SS\r\nSE\r\n")
        val reader = CrlfFrameReader(conn)

        assertEquals("SS", reader.nextFrame())
        assertEquals("SE", reader.nextFrame())
        assertNull(reader.nextFrame())
    }

    @Test
    fun `handles a frame delivered byte by byte`() {
        val conn = FakeConnection("IR")
        conn.maxRead = 1
        val reader = CrlfFrameReader(conn)

        conn.push("D057\r\n")
        var frame: String? = null
        for (i in 0 until 10) {
            frame = reader.nextFrame()
            if (frame != null) break
        }
        assertEquals("IRD057", frame)
    }

    @Test
    fun `drops an oversized line and resyncs on the next frame`() {
        val conn = FakeConnection()
        val reader = CrlfFrameReader(conn, maxLineBytes = 4)

        conn.push("ABCDEFGH") // 8 bytes, no CR LF
        assertNull(reader.nextFrame())

        conn.push("OK\r\n")
        assertEquals("OK", reader.nextFrame())
    }

    private class FakeConnection(feed: String = "") : SerialConnection {
        private var data: ByteArray = feed.toByteArray()
        var maxRead: Int = Int.MAX_VALUE

        override val isOpen: Boolean
            get() = true

        override fun open() = Unit

        override fun write(data: ByteArray) = Unit

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (data.isEmpty()) return 0
            val n = minOf(data.size, length, maxRead)
            data.copyInto(buffer, offset, 0, n)
            data = data.copyOfRange(n, data.size)
            return n
        }

        override fun close() = Unit

        fun push(bytes: String) {
            data += bytes.toByteArray()
        }
    }
}
