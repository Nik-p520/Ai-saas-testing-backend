package com.nikhilpanwar.Ai_saas_testing.service.sse;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    // Thread-safe map to store active connections for each testId
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Creates a new connection for a specific test ID.
     * The Frontend calls this to "subscribe" to updates.
     */
    public SseEmitter subscribe(String testId) {
        // Timeout set to 5 minutes (300,000ms) or however long your longest test takes
        SseEmitter emitter = new SseEmitter(300000L);

        emitters.put(testId, emitter);

        emitter.onCompletion(() -> emitters.remove(testId));
        emitter.onTimeout(() -> {
            emitters.remove(testId);
            // Optionally send a timeout error to client
        });
        emitter.onError((e) -> emitters.remove(testId));

        return emitter;
    }

    /**
     * Call this method from your TestService to update the UI
     */
    public void sendProgress(String testId, String statusMessage) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter != null) {
            try {
                // We send a structured event: "PROGRESS"
                emitter.send(SseEmitter.event()
                        .name("PROGRESS")
                        .data(statusMessage));
            } catch (IOException e) {
                emitters.remove(testId);
            }
        }
    }

    /**
     * Call this when the test is fully done to send the final result
     */
    public void sendResult(String testId, TestResultDTO result) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter != null) {
            try {
                // Send the final result object
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(result));
                // Close the connection
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(testId);
            }
        }
    }

    // Optional: Send an error event
    public void sendError(String testId, String errorMessage) {
        SseEmitter emitter = emitters.get(testId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("ERROR").data(errorMessage));
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(testId);
            }
        }
    }
}