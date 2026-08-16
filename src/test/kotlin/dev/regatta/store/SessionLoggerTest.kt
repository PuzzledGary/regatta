package dev.regatta.store

import dev.regatta.config.RegattaProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class SessionLoggerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes one json line per record in order`() {
        val props = RegattaProperties(sessions = RegattaProperties.Sessions(tempDir))
        val logger = SessionLogger(props, mapper())
        val start = Instant.parse("2026-08-16T13:42:00Z")

        val file = logger.open(start)
        logger.append(HeaderRecord(startedAt = start, firmware = "IV40210"))
        logger.append(SessionStartRecord(timestamp = start, workoutDistanceMeters = 1000))
        logger.append(ReadingRecord(timestamp = start, distanceMeters = 12.5, strokes = 4))
        logger.append(PacketRecord(timestamp = start, packet = "P 12"))
        logger.append(SessionEndRecord(timestamp = start, reason = "USER_STOP", monitorDistanceMeters = 1000.5))
        logger.close()

        val lines = Files.readAllLines(file)
        assertEquals(5, lines.size)
        assertTrue(lines[0].contains("\"type\":\"header\""))
        assertTrue(lines[1].contains("\"type\":\"session_start\""))
        assertTrue(lines[1].contains("\"workoutDistanceMeters\":1000"))
        assertTrue(lines[2].contains("\"type\":\"reading\""))
        assertTrue(lines[3].contains("\"type\":\"packet\""))
        assertTrue(lines[3].contains("\"packet\":\"P 12\""))
        assertTrue(lines[4].contains("\"type\":\"session_end\""))
        assertTrue(lines[4].contains("\"reason\":\"USER_STOP\""))
    }

    @Test
    fun `omits null fields`() {
        val props = RegattaProperties(sessions = RegattaProperties.Sessions(tempDir))
        val logger = SessionLogger(props, mapper())
        val file = logger.open(Instant.parse("2026-08-16T13:42:00Z"))
        logger.append(ReadingRecord(timestamp = Instant.parse("2026-08-16T13:42:01Z"), distanceMeters = 12.5))
        logger.close()

        val line = Files.readString(file)
        assertTrue(line.contains("\"distanceMeters\":12.5"))
        assertFalse(line.contains("strokeRate"))
        assertFalse(line.contains("watts"))
        assertFalse(line.contains("kcalTotal"))
    }

    @Test
    fun `buffers until flush then writes`() {
        val props = RegattaProperties(sessions = RegattaProperties.Sessions(tempDir))
        val logger = SessionLogger(props, mapper())
        val file = logger.open(Instant.parse("2026-08-16T13:42:00Z"))
        logger.append(ReadingRecord(timestamp = Instant.parse("2026-08-16T13:42:01Z"), distanceMeters = 1.0))

        assertTrue(Files.readString(file).isEmpty())
        logger.flush()
        assertTrue(Files.readString(file).contains("\"type\":\"reading\""))
        logger.close()
    }

    @Test
    fun `closed logger ignores appends and later sessions reuse a new file`() {
        val props = RegattaProperties(sessions = RegattaProperties.Sessions(tempDir))
        val logger = SessionLogger(props, mapper())

        val first = logger.open(Instant.parse("2026-08-16T13:42:00Z"))
        logger.close()
        logger.append(ReadingRecord(timestamp = Instant.parse("2026-08-16T13:42:01Z"), distanceMeters = 1.0))

        val second = logger.open(Instant.parse("2026-08-16T13:45:00Z"))
        assertTrue(first.toString() != second.toString())
        assertEquals(0, Files.readString(first).length)
        assertTrue(Files.readString(second).isEmpty())
        logger.close()
    }

    private fun mapper() = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
}
