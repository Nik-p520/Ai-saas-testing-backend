package com.nikhilpanwar.Ai_saas_testing.Test;

import com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards.StatsService;
import com.nikhilpanwar.Ai_saas_testing.service.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private static final String PYTHON_FULL_AUDIT_URL = "http://localhost:5000/test-website";
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
            sseService.sendProgress(streamId, "Launching Cloud Browser & Generating User Journey...");

            Map<String, Object> request = new HashMap<>();
            request.put("url", requestDTO.getUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(PYTHON_FULL_AUDIT_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                sseService.sendProgress(streamId, "AI Analysis Complete. Saving Report...");
                Map<String, Object> body = response.getBody();

                // ✅ UPDATED LOGIC HERE: Status depends ONLY on execution success
                Boolean success = (Boolean) body.getOrDefault("success", false);
                String finalStatus = Boolean.TRUE.equals(success) ? "passed" : "failed";

                // ✅ UPDATE: Extract duration sent by Python
                String duration = (String) body.getOrDefault("duration", "0s");

                List<String> logs = extractStringList(body, "logs");

                // --- PROCESS BUGS ---
                List<TestResult.BugItem> bugs = new ArrayList<>();
                List<Map<String, Object>> rawBugs = (List<Map<String, Object>>) body.get("bugs");
                if (rawBugs != null) {
                    for (Map<String, Object> b : rawBugs) {
                        bugs.add(TestResult.BugItem.builder()
                                .bugId(UUID.randomUUID().toString())
                                .title((String) b.getOrDefault("title", "Detected Issue"))
                                .description((String) b.getOrDefault("description", ""))
                                .severity(((String) b.getOrDefault("severity", "medium")).toLowerCase())
                                .build());
                    }
                }

                // --- PROCESS RECOMMENDATIONS ---
                List<TestResult.Recommendation> recommendations = new ArrayList<>();
                List<Map<String, Object>> rawRecs = (List<Map<String, Object>>) body.get("recommendations");
                if (rawRecs != null) {
                    for (Map<String, Object> r : rawRecs) {
                        recommendations.add(TestResult.Recommendation.builder()
                                .recommendationId(UUID.randomUUID().toString())
                                .title((String) r.getOrDefault("title", "Suggestion"))
                                .description((String) r.getOrDefault("description", ""))
                                .impact(((String) r.getOrDefault("priority", "medium")).toLowerCase())
                                .category(((String) r.getOrDefault("category", "general")).toLowerCase())
                                .build());
                    }
                }

                // --- PROCESS SCREENSHOTS ---
                List<TestResult.Screenshot> screenshots = new ArrayList<>();
                List<Map<String, Object>> rawShots = (List<Map<String, Object>>) body.get("screenshots");
                if (rawShots != null) {
                    Files.createDirectories(SCREENSHOT_DIR);
                    for (Map<String, Object> shot : rawShots) {
                        String rawName = (String) shot.getOrDefault("name", "screenshot");

                        // ✅ FIX: Prepend UUID to make filename unique for EVERY test run
                        String filename = UUID.randomUUID().toString() + "_" + rawName + ".png";

                        String b64Data = (String) shot.get("data");

                        if (b64Data != null && !b64Data.isEmpty()) {
                            try {
                                byte[] bytes = Base64.getDecoder().decode(b64Data);
                                Path filePath = SCREENSHOT_DIR.resolve(filename);
                                Files.write(filePath, bytes);

                                screenshots.add(TestResult.Screenshot.builder()
                                        .url("http://localhost:8080/uploads/screenshots/" + filename)
                                        .caption((String) shot.getOrDefault("description", "Test Screenshot"))
                                        .build());
                            } catch (Exception e) {
                                logs.add("⚠️ Failed to save screenshot: " + e.getMessage());
                            }
                        }
                    }
                }

                String realScript = (String) body.getOrDefault("script", "// Script not provided by AI Agent");

                // --- SAVE TO DB ---
                TestResult result = TestResult.builder()
                        .userId(userId)
                        .websiteUrl(requestDTO.getUrl())
                        .status(finalStatus)
                        .executionTime(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .script(realScript)
                        .duration(duration) // ✅ UPDATE: Using real duration from Python
                        .browser("firefox")
                        .logs(logs)
                        .bugs(bugs)
                        .recommendations(recommendations)
                        .screenshots(screenshots)
                        .build();

                TestResult savedResult = testRepository.save(result);

                pushLiveUpdates();
                sseService.sendResult(streamId, convertToDTO(savedResult));

            } else {
                throw new RuntimeException("AI Service returned error status: " + response.getStatusCode());
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

    private List<String> extractStringList(Map<String, Object> map, String key) {
        try {
            Object val = map.get(key);
            if (val instanceof List<?>) {
                List<?> list = (List<?>) val;
                List<String> result = new ArrayList<>();
                for (Object o : list) result.add(String.valueOf(o));
                return result;
            }
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private TestResultDTO convertToDTO(TestResult test) {
        return TestResultDTO.builder()
                .id(test.getId())
                .websiteUrl(test.getWebsiteUrl())
                .executionTime(test.getExecutionTime())
                .duration(test.getDuration())
                .browser(test.getBrowser())
                .status(test.getStatus())
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