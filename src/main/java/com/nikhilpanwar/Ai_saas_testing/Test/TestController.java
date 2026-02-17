package com.nikhilpanwar.Ai_saas_testing.Test;

import com.nikhilpanwar.Ai_saas_testing.service.sse.SseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestService testService;
    private final SseService sseService;

    public TestController(TestService testService, SseService sseService) {
        this.testService = testService;
        this.sseService = sseService;
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "Anonymous";
    }

    @PostMapping("/generate")
    public ResponseEntity<String> startTest(@RequestBody TestRequestDTO request) {
        String userId = getCurrentUserId();
        String testId = UUID.randomUUID().toString();

        System.out.println("🚀 Test Request Received. ID: " + testId);

        // Run Async
        CompletableFuture.runAsync(() -> {
            try {
                // 🛑 CRITICAL FIX: Wait 2 seconds for Frontend to connect
                // This prevents the "First Event Lost" issue
                Thread.sleep(2000);

                testService.generateAndExecuteTestAsync(testId, userId, request);
            } catch (Exception e) {
                sseService.sendError(testId, "Test failed to start: " + e.getMessage());
            }
        });

        // Return ID immediately
        return ResponseEntity.ok(testId);
    }

    @GetMapping(path = "/stream/{testId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTestProgress(@PathVariable String testId) {
        // 5-minute timeout
        SseEmitter emitter = new SseEmitter(300000L);

        // Register with service
        this.sseService.subscribe(testId, emitter);

        // Wrap in try-catch to fix the "Unhandled IOException" error
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected"));
        } catch (IOException e) {
            this.sseService.unsubscribe(testId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/result/{testId}")
    public ResponseEntity<TestResultDTO> getTestResult(@PathVariable String testId) {
        return ResponseEntity.ok(testService.getTestResult(testId));
    }

    @GetMapping("/results")
    public ResponseEntity<List<TestResultDTO>> getAllResults() {
        return ResponseEntity.ok(testService.getAllTestResults());
    }

    @DeleteMapping("/delete/{testId}")
    public ResponseEntity<Void> deleteTestResult(@PathVariable String testId) {
        return testService.deleteTestResult(testId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}