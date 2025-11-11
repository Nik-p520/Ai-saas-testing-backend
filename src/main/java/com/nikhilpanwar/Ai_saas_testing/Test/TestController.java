package com.nikhilpanwar.Ai_saas_testing.Test;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @PostMapping("/generate")
    public ResponseEntity<TestResultDTO> generateTest(@RequestBody TestRequestDTO request) {
        TestResultDTO resultDTO = testService.generateAndExecuteTest(request);
        return ResponseEntity.ok(resultDTO);
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
        boolean deleted = testService.deleteTestResult(testId);
        if (deleted) {
            return ResponseEntity.noContent().build(); // 204 - success, no response body
        } else {
            return ResponseEntity.notFound().build(); // 404 - not found
        }
    }
}
