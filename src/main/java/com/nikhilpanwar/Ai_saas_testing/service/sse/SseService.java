package com.nikhilpanwar.Ai_saas_testing.service.sse;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String testId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.put(testId, emitter);

        emitter.onCompletion(() -> emitters.remove(testId));
        emitter.onTimeout(() -> emitters.remove(testId));
        emitter.onError(e -> emitters.remove(testId));

        return emitter;
    }

    // ✅ PROGRESS → STRING ONLY
    public void sendProgress(String testId, String message) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("PROGRESS")
                            .data(message)
            );
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }

    // ✅ RESULT → STRING(JSON)
    public void sendResult(String testId, TestResultDTO result) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("COMPLETED")
                            .data(result) // Jackson auto JSON
            );
            emitter.complete();
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }

    // ✅ ERROR → STRING
    public void sendError(String testId, String error) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("ERROR")
                            .data(error)
            );
            emitter.complete();
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }
}
