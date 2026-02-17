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

    // ✅ ONE shared thread pool for all heartbeats (Prevents Memory Leaks)
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Subscribes a client to a test stream.
     */
    public void subscribe(String testId, SseEmitter emitter) {
        emitters.put(testId, emitter);

        // Cleanup logic
        emitter.onCompletion(() -> unsubscribe(testId));
        emitter.onTimeout(() -> unsubscribe(testId));
        emitter.onError(e -> unsubscribe(testId));

        // ✅ Use the shared executor to send heartbeats every 15 seconds
        // This keeps the Render/Proxy connection alive.
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                SseEmitter activeEmitter = emitters.get(testId);
                if (activeEmitter != null) {
                    activeEmitter.send(SseEmitter.event()
                            .name("ping")
                            .comment("keep-alive"));
                }
            } catch (Exception e) {
                unsubscribe(testId);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    // ✅ Make this PUBLIC so your Controller can call it if needed
    public void unsubscribe(String testId) {
        emitters.remove(testId);
        System.out.println("🧹 Cleaned up connection for: " + testId);
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
            unsubscribe(testId);
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
            emitter.complete(); // Successfully close connection
        } catch (IOException e) {
            unsubscribe(testId);
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
            unsubscribe(testId);
        }
    }
}