package dev.regatta.store

import dev.regatta.config.RegattaProperties
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class SessionLogger(
    private val properties: RegattaProperties,
    private val mapper: ObjectMapper,
) {

    private val writerRef: AtomicReference<BufferedWriter?> = AtomicReference()

    @Volatile
    private var pathValue: Path? = null

    val path: Path?
        get() = pathValue

    fun open(startedAt: Instant): Path {
        close()
        val dir = properties.sessions.directory
        Files.createDirectories(dir)
        val file = dir.resolve(startedAt.toString().replace(':', '-') + ".jsonl")
        val writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)
        writerRef.set(writer)
        pathValue = file
        return file
    }

    fun append(record: LogRecord) {
        writerRef.get()?.run {
            write(mapper.writeValueAsString(record))
            newLine()
        }
    }

    fun flush() {
        writerRef.get()?.flush()
    }

    fun close() {
        writerRef.getAndSet(null)?.run {
            flush()
            close()
        }
        pathValue = null
    }

    @Scheduled(fixedDelayString = "\${regatta.session.flush-interval:5s}")
    fun scheduledFlush() {
        flush()
    }

    @PreDestroy
    fun shutdown() {
        close()
    }
}
