package dev.regatta.serial

import java.nio.charset.StandardCharsets

class FakeSerialConnection : SerialConnection {
    val sent = mutableListOf<String>()
    private val inbound = ArrayDeque<Byte>()
    var closed = false

    override val isOpen: Boolean
        get() = !closed

    override fun open() = Unit

    override fun write(data: ByteArray) {
        sent += String(data, StandardCharsets.US_ASCII)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (inbound.isEmpty()) return 0
        val n = minOf(inbound.size, length)
        repeat(n) { buffer[offset + it] = inbound.removeFirst() }
        return n
    }

    override fun close() {
        closed = true
    }

    fun feed(line: String) {
        line.toByteArray(StandardCharsets.US_ASCII).forEach { inbound.addLast(it) }
    }
}
