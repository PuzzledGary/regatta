package dev.regatta.api

import dev.regatta.session.Reading
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArraySet

@Service
class LiveReadingService {

    private val emitters: MutableSet<SseEmitter> = CopyOnWriteArraySet()

    @Volatile
    private var latestValue: Reading? = null

    val latest: Reading?
        get() = latestValue

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(0L)
        emitters += emitter
        emitter.onCompletion { emitters -= emitter }
        emitter.onTimeout { emitters -= emitter }
        emitter.onError { emitters -= emitter }
        latestValue?.let { send(it, emitter) }
        return emitter
    }

    fun publish(reading: Reading) {
        latestValue = reading
        emitters.forEach { send(reading, it) }
    }

    private fun send(reading: Reading, emitter: SseEmitter) {
        try {
            emitter.send(SseEmitter.event().name("reading").data(reading))
        } catch (_: Exception) {
            emitters -= emitter
        }
    }
}
