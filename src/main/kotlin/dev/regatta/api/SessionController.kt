package dev.regatta.api

import dev.regatta.session.Reading
import dev.regatta.session.SessionStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SessionSnapshot(
    val status: SessionStatus,
    val reading: Reading?,
)

@RestController
@RequestMapping("/api/session")
class SessionController(private val live: LiveReadingService) {

    @GetMapping
    fun current(): SessionSnapshot = SessionSnapshot(SessionStatus.IDLE, live.latest)
}
