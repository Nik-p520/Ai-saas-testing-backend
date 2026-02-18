package com.nikhilpanwar.Ai_saas_testing.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards.StatsService;
import com.nikhilpanwar.Ai_saas_testing.service.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestResultRepository testRepository;
    private final RestTemplate restTemplate;
    private final StatsService statsService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    private static final String PYTHON_FULL_AUDIT_URL =
            System.getenv("SPRING_PROFILES_ACTIVE") != null
                    ? "https://ai-saas-testing-backend-1.onrender.com/test-website"
                    : "http://localhost:5000/test-website";

    private static final Path SCREENSHOT_DIR = Paths.get("uploads/screenshots");

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        throw new RuntimeException("User is not authenticated");
    }

    public void generateAndExecuteTestAsync(String streamId, String userId, TestRequestDTO requestDTO) {
        System.out.println("🚀 Starting Async Test. Stream ID: " + streamId + " for URL: " + requestDTO.getUrl());

        try {
            sseService.sendProgress(streamId, "Connecting to AI Agent...");

            Map<String, Object> request = new HashMap<>();
            request.put("url", requestDTO.getUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // ✅ Headers to prevent Cloudflare from flagging Render as a bot
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = null;
            int maxRetries = 2;
            int attempt = 0;

            while (attempt <= maxRetries) {
                try {
                    sseService.sendProgress(streamId, "Launching Cloud Browser & Analyzing Site...");
                    response = restTemplate.postForEntity(PYTHON_FULL_AUDIT_URL, entity, String.class);

                    // ✅ Check if the response is actually valid JSON and not a Cloudflare HTML page
                    if (response.getBody() != null && response.getBody().trim().startsWith("<!DOCTYPE html>")) {
                        throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Received HTML instead of JSON");
                    }

                    break; // Success! Exit loop
                } catch (HttpClientErrorException e) {
                    attempt++;
                    if (attempt > maxRetries) throw e;

                    sseService.sendProgress(streamId, "⚠️ AI Service busy (Rate Limit). Retrying in 5s... (Attempt " + attempt + ")");
                    Thread.sleep(5000); // Wait 5 seconds before retrying
                }
            }

            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                sseService.sendProgress(streamId, "AI Analysis Complete. Processing Results...");

                // ✅ Guard parsing logic
                Map<String, Object> body;
                try {
                    body = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception parseError) {
                    System.err.println("❌ Parsing failed: " + parseError.getMessage());
                    throw new RuntimeException("AI Service returned invalid data format.");
                }

                String finalStatus = (String) body.getOrDefault("status", "success");
                Object durationObj = body.getOrDefault("test_duration", 0);
                String duration = durationObj.toString() + "s";

                Integer healthScore = 0;
                if (body.containsKey("health_scores")) {
                    Map<String, Object> healthData = (Map<String, Object>) body.get("health_scores");
                    if (healthData != null && healthData.get("overall") != null) {
                        healthScore = ((Number) healthData.get("overall")).intValue();
                    }
                }

                // ... [Bugs, Recommendations, and Screenshots logic remains the same] ...
                List<TestResult.BugItem> bugs = new ArrayList<>();
                List<Map<String, Object>> rawBugs = (List<Map<String, Object>>) body.get("bugs_found");
                if (rawBugs != null) {
                    for (Map<String, Object> b : rawBugs) {
                        bugs.add(TestResult.BugItem.builder()
                                .bugId(UUID.randomUUID().toString())
                                .title((String) b.getOrDefault("type", "Detected Issue"))
                                .description((String) b.getOrDefault("message", ""))
                                .severity(((String) b.getOrDefault("severity", "medium")).toLowerCase())
                                .build());
                    }
                }

                List<TestResult.Recommendation> recommendations = new ArrayList<>();
                List<Map<String, Object>> rawRecs = (List<Map<String, Object>>) body.get("recommendations");
                if (rawRecs != null) {
                    for (Map<String, Object> r : rawRecs) {
                        List<String> actions = (List<String>) r.get("actions");
                        String desc = (actions != null) ? String.join(". ", actions) : "";
                        recommendations.add(TestResult.Recommendation.builder()
                                .recommendationId(UUID.randomUUID().toString())
                                .title((String) r.getOrDefault("title", "Suggestion"))
                                .description(desc)
                                .impact(((String) r.getOrDefault("priority", "medium")).toLowerCase())
                                .category("optimization")
                                .build());
                    }
                }

                List<TestResult.Screenshot> screenshots = new ArrayList<>();
                List<String> rawShots = (List<String>) body.get("screenshots");
                if (rawShots != null) {
                    Files.createDirectories(SCREENSHOT_DIR);
                    for (String b64Data : rawShots) {
                        if (b64Data != null && !b64Data.isEmpty()) {
                            try {
                                String filename = UUID.randomUUID().toString() + ".png";
                                byte[] bytes = Base64.getDecoder().decode(b64Data);
                                Files.write(SCREENSHOT_DIR.resolve(filename), bytes);
                                screenshots.add(TestResult.Screenshot.builder()
                                        .url("https://your-backend-url.com/uploads/screenshots/" + filename)
                                        .caption("Audit Screenshot")
                                        .build());
                            } catch (Exception e) {
                                System.err.println("Failed to save screenshot: " + e.getMessage());
                            }
                        }
                    }
                }

                List<String> logs = new ArrayList<>();
                if (body.containsKey("ai_summary")) {
                    Map<String, Object> aiSum = (Map<String, Object>) body.get("ai_summary");
                    logs.add((String) aiSum.getOrDefault("executive_summary", "Test finished."));
                }

                TestResult result = TestResult.builder()
                        .userId(userId)
                        .websiteUrl(requestDTO.getUrl())
                        .status(finalStatus.equals("success") ? "passed" : "failed")
                        .executionTime(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .duration(duration)
                        .browser("chromium")
                        .healthScore(healthScore)
                        .logs(logs)
                        .bugs(bugs)
                        .recommendations(recommendations)
                        .screenshots(screenshots)
                        .script("// Playwright automated test complete")
                        .build();

                TestResult savedResult = testRepository.save(result);
                pushLiveUpdates();
                sseService.sendResult(streamId, convertToDTO(savedResult));

            } else {
                throw new RuntimeException("Flask AI Service failed after connection.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sseService.sendError(streamId, "Critical Error: " + e.getMessage());
            saveFailedRecord(userId, requestDTO.getUrl(), e.getMessage());
        }
    }

    private void saveFailedRecord(String userId, String url, String errorMsg) {
        try {
            TestResult failed = TestResult.builder()
                    .userId(userId)
                    .websiteUrl(url)
                    .status("failed")
                    .healthScore(0)
                    .executionTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .logs(List.of("Critical Failure: " + errorMsg))
                    .build();
            testRepository.save(failed);
            pushLiveUpdates();
        } catch (Exception ignored) {}
    }

    private void pushLiveUpdates() {
        try {
            statsService.getStats();
            statsService.getTestTrends();
            statsService.getDistribution();
        } catch (Exception e) {
            System.err.println("⚠️ Failed to push live stats: " + e.getMessage());
        }
    }

    private TestResultDTO convertToDTO(TestResult test) {
        return TestResultDTO.builder()
                .id(test.getId())
                .websiteUrl(test.getWebsiteUrl())
                .executionTime(test.getExecutionTime())
                .duration(test.getDuration())
                .browser(test.getBrowser())
                .status(test.getStatus())
                .healthScore(test.getHealthScore() != null ? test.getHealthScore() : 0)
                .logs(test.getLogs() != null ? test.getLogs() : new ArrayList<>())
                .screenshots(test.getScreenshots() != null
                        ? test.getScreenshots().stream()
                        .map(s -> new TestResultDTO.Screenshot(s.getUrl(), s.getCaption()))
                        .toList()
                        : new ArrayList<>())
                .bugs(test.getBugs() != null
                        ? test.getBugs().stream()
                        .map(b -> new TestResultDTO.BugItem(b.getBugId(), b.getTitle(), b.getDescription(), b.getSeverity()))
                        .toList()
                        : new ArrayList<>())
                .recommendations(test.getRecommendations() != null
                        ? test.getRecommendations().stream()
                        .map(r -> new TestResultDTO.Recommendation(
                                r.getRecommendationId(),
                                r.getTitle(),
                                r.getDescription(),
                                r.getImpact(),
                                r.getCategory()))
                        .toList()
                        : new ArrayList<>())
                .script(test.getScript())
                .build();
    }

    public TestResultDTO getTestResult(String id) {
        String currentUserId = getCurrentUserId();
        TestResult test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));

        if (!test.getUserId().equals(currentUserId)) {
            throw new RuntimeException("Unauthorized: You cannot view this test.");
        }
        return convertToDTO(test);
    }

    public List<TestResultDTO> getAllTestResults() {
        String currentUserId = getCurrentUserId();
        return testRepository.findByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    public boolean deleteTestResult(String testId) {
        String currentUserId = getCurrentUserId();
        Optional<TestResult> testOpt = testRepository.findById(testId);

        if (testOpt.isPresent()) {
            TestResult test = testOpt.get();
            if (test.getUserId().equals(currentUserId)) {
                testRepository.deleteById(testId);
                pushLiveUpdates();
                return true;
            }
        }
        return false;
    }
}