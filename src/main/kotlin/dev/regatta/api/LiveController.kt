package dev.regatta.api

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/live")
class LiveController(private val live: LiveReadingService) {

    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter = live.subscribe()
}
