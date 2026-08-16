package dev.regatta.capture

import dev.regatta.config.RegattaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CaptureConfig {

    @Bean
    fun captureDevice(properties: RegattaProperties): CaptureDevice =
        S4CaptureDevice(properties)
}
