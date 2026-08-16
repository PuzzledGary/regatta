package dev.regatta.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties(prefix = "regatta")
data class RegattaProperties(
    val serial: Serial = Serial(),
    val session: Session = Session(),
    val sessions: Sessions = Sessions(),
    val demo: Demo = Demo(),
) {
    data class Serial(
        val device: String? = null,
        val baudRate: Int = 19200,
        val dataBits: Int = 8,
        val stopBits: Int = 1,
        val parity: Int = 0,
    )

    data class Session(
        val pollInterval: Duration = Duration.ofSeconds(1),
        val workoutDistanceMeters: Int? = null,
        val workoutDurationSeconds: Long? = null,
        val flushInterval: Duration = Duration.ofSeconds(5),
        val metadata: Boolean = false,
    )

    data class Sessions(
        val directory: Path = Path.of("sessions"),
    )

    data class Demo(
        val enabled: Boolean = true,
    )
}
