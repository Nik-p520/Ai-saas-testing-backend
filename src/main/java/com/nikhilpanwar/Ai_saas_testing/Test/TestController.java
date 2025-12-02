package com.nikhilpanwar.Ai_saas_testing.Test;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    // ✅ Helper to get Firebase UID
    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        return "Anonymous";
    }

    @PostMapping("/generate")
    public ResponseEntity<TestResultDTO> generateTest(@RequestBody TestRequestDTO request) {
        System.out.println("🚀 Test Generation Requested by: " + getCurrentUserId());
        // Pass UserID to service if needed, or let service handle it
        TestResultDTO resultDTO = testService.generateAndExecuteTest(request);
        return ResponseEntity.ok(resultDTO);
    }

    @GetMapping("/result/{testId}")
    public ResponseEntity<TestResultDTO> getTestResult(@PathVariable String testId) {
        return ResponseEntity.ok(testService.getTestResult(testId));
    }

    @GetMapping("/results")
    public ResponseEntity<List<TestResultDTO>> getAllResults() {
        String userId = getCurrentUserId();
        System.out.println("📊 Fetching All Results for User: " + userId);

        // Note: Tera Service shayad saare results la raha hai.
        // Ideal way ye hai ki tu userId pass kare: testService.getAllTestResults(userId);
        return ResponseEntity.ok(testService.getAllTestResults());
    }

    @DeleteMapping("/delete/{testId}")
    public ResponseEntity<Void> deleteTestResult(@PathVariable String testId) {
        boolean deleted = testService.deleteTestResult(testId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}