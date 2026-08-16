package dev.regatta.api

import dev.regatta.session.Reading
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "regatta.demo", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DemoLivePublisher(
    private val live: LiveReadingService,
) {

    @Scheduled(fixedDelayString = "1000")
    fun publish() {
        val reading = Reading(
            timestamp = Instant.now(),
            distanceMeters = Math.random() * 500,
            elapsedSeconds = 60,
            strokes = 120,
            strokeRate = 22.0 + Math.random() * 6,
            speedMps = 2.0 + Math.random(),
            pacePer500mSeconds = 220.0 - Math.random() * 30,
            watts = 150.0 + Math.random() * 50,
            kcalCurrent = Math.random() * 10,
            kcalTotal = Math.random() * 300,
        )
        live.publish(reading)
    }
}
