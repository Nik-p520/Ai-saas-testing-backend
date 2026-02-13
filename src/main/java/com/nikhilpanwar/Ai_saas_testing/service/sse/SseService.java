package com.nikhilpanwar.Ai_saas_testing.service.sse;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Subscribes a client to a test stream.
     * Receives the emitter created in the Controller to maintain the connection.
     */
    public void subscribe(String testId, SseEmitter emitter) {
        // Store the emitter
        emitters.put(testId, emitter);

        // Cleanup logic
        emitter.onCompletion(() -> emitters.remove(testId));
        emitter.onTimeout(() -> emitters.remove(testId));
        emitter.onError(e -> emitters.remove(testId));

        // ✅ HEARTBEAT: Sends a 'ping' every 15 seconds.
        // This prevents Render/Browsers from closing the connection due to inactivity.
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("ping")
                        .comment("keep-alive"));
            } catch (Exception e) {
                emitters.remove(testId);
                executor.shutdown();
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    // ✅ PROGRESS Updates
    public void sendProgress(String testId, String message) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("PROGRESS")
                    .data(message));
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }

    // ✅ COMPLETION Updates
    public void sendResult(String testId, TestResultDTO result) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("COMPLETED")
                    .data(result));
            emitter.complete(); // Close connection after success
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }

    // ✅ ERROR Updates
    public void sendError(String testId, String error) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("ERROR")
                    .data(error));
            emitter.complete(); // Close connection after error
        } catch (IOException e) {
            emitters.remove(testId);
        }
    }
}