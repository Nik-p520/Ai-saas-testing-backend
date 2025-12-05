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
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestResultRepository testRepository;
    private final RestTemplate restTemplate;
    private final StatsService statsService;
    private final SseService sseService;

    private static final String PYTHON_API_URL = "http://localhost:5000/generate-tests";
    private static final String PYTHON_EXECUTE_URL = "http://localhost:5000/execute-tests";
    private static final Path SCREENSHOT_DIR = Paths.get("uploads/screenshots");

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName();
        }
        throw new RuntimeException("User is not authenticated");
    }

    /**
     * Call Python Flask AI service to generate Playwright script
     */
    public String generateScript(TestRequestDTO requestDTO) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("url", requestDTO.getUrl());
            request.put("test_requirements", requestDTO.getTestRequirements());
            request.put("credentials", requestDTO.getCredentials());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<GeneratedScriptDTO> response =
                    restTemplate.postForEntity(PYTHON_API_URL, entity, GeneratedScriptDTO.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                GeneratedScriptDTO dto = response.getBody();
                return (dto.isSuccess() && dto.getTest_script() != null)
                        ? dto.getTest_script()
                        : "// ❌ AI generation failed: " + dto.getError();
            } else {
                return "// ❌ AI service returned non-2xx response";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "// ⚠️ Error calling AI service: " + e.getMessage();
        }
    }

    /**
     * ASYNC Method for Real-Time Updates
     */
    public void generateAndExecuteTestAsync(String streamId, String userId, TestRequestDTO requestDTO) {
        System.out.println("🚀 Starting Async Test. Stream ID: " + streamId + " for URL: " + requestDTO.getUrl());

        try {
            // STEP 1: GENERATION
            sseService.sendProgress(streamId, "Analyzing requirements & Generating AI Script...");
            String script = generateScript(requestDTO);

            // Handle Generation Failure
            if (script.startsWith("//")) {
                sseService.sendProgress(streamId, "❌ Script generation failed. Finalizing...");

                TestResult failed = TestResult.builder()
                        // 🛑 REMOVED .id(streamId) -> Let DB generate the ID
                        .userId(userId)
                        .websiteUrl(requestDTO.getUrl())
                        .status("failed")
                        .executionTime(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .script(script)
                        .logs(List.of("Script generation failed"))
                        .build();

                TestResult savedFailed = testRepository.save(failed);

                // Send the result with the NEW DB generated ID
                sseService.sendResult(streamId, convertToDTO(savedFailed));
                return;
            }

            // STEP 2: EXECUTION
            sseService.sendProgress(streamId, "Script Generated. Launching Cloud Browser...");
            Thread.sleep(500);
            sseService.sendProgress(streamId, "Executing Test Script (this may take a moment)...");

            List<String> logs = new ArrayList<>();
            List<TestResult.BugItem> bugs = new ArrayList<>();
            List<TestResult.Recommendation> recommendations = new ArrayList<>();
            List<TestResult.Screenshot> screenshots = new ArrayList<>();
            String status = "failed";
            String duration = "0s";
            String browser = "chromium";

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                Map<String, Object> execRequest = new HashMap<>();
                execRequest.put("test_script", script);
                execRequest.put("url", requestDTO.getUrl());
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(execRequest, headers);

                ResponseEntity<Map> response =
                        restTemplate.postForEntity(PYTHON_EXECUTE_URL, entity, Map.class);

                sseService.sendProgress(streamId, "Processing Results & Screenshots...");

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> execution = response.getBody();
                    Boolean success = (Boolean) execution.get("success");
                    status = (String) execution.getOrDefault("status", (success != null && success) ? "passed" : "failed");
                    logs = extractStringList(execution, "logs");
                    duration = (String) execution.getOrDefault("duration", "0s");
                    browser = (String) execution.getOrDefault("browser", "chromium");

                    // Extract Bugs
                    List<Map<String, Object>> bugList = (List<Map<String, Object>>) execution.get("bugs");
                    if (bugList != null) {
                        for (Map<String, Object> b : bugList) {
                            bugs.add(TestResult.BugItem.builder()
                                    .bugId((String) b.getOrDefault("bugId", UUID.randomUUID().toString()))
                                    .title((String) b.getOrDefault("title", "Unknown Bug"))
                                    .description((String) b.getOrDefault("description", ""))
                                    .severity((String) b.getOrDefault("severity", "medium"))
                                    .build());
                        }
                    }

                    // Extract Recommendations
                    List<Map<String, Object>> recList = (List<Map<String, Object>>) execution.get("recommendations");
                    if (recList != null) {
                        for (Map<String, Object> r : recList) {
                            recommendations.add(TestResult.Recommendation.builder()
                                    .recommendationId((String) r.getOrDefault("recommendationId", UUID.randomUUID().toString()))
                                    .title((String) r.getOrDefault("title", "Recommendation"))
                                    .description((String) r.getOrDefault("description", ""))
                                    .impact((String) r.getOrDefault("impact", "medium"))
                                    .category((String) r.getOrDefault("category", "ux"))
                                    .build());
                        }
                    }

                    // Extract Screenshots
                    List<Map<String, Object>> shots = (List<Map<String, Object>>) execution.get("screenshots");
                    if (shots != null) {
                        Files.createDirectories(SCREENSHOT_DIR);
                        for (Map<String, Object> shot : shots) {
                            String filename = (String) shot.getOrDefault("filename", UUID.randomUUID() + ".png");
                            String b64 = (String) shot.get("b64");
                            if (b64 != null) {
                                try {
                                    byte[] bytes = Base64.getDecoder().decode(b64);
                                    Files.write(SCREENSHOT_DIR.resolve(filename), bytes);
                                    screenshots.add(TestResult.Screenshot.builder()
                                            .url("/uploads/screenshots/" + filename)
                                            .caption(filename)
                                            .build());
                                } catch (Exception e) {
                                    logs.add("⚠️ Failed to save screenshot: " + filename);
                                }
                            }
                        }
                    }
                } else {
                    logs.add("❌ Flask returned non-2xx: " + response.getStatusCode());
                }
            } catch (Exception e) {
                logs.add("❌ Exception: " + e.getMessage());
                e.printStackTrace();
            }

            // STEP 3: SAVE AND FINISH
            sseService.sendProgress(streamId, "Saving Report to Database...");

            TestResult result = TestResult.builder()
                    // 🛑 REMOVED .id(streamId) -> Let DB generate it!
                    .userId(userId)
                    .websiteUrl(requestDTO.getUrl())
                    .status(status)
                    .executionTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .script(script)
                    .duration(duration)
                    .browser(browser)
                    .logs(logs)
                    .bugs(bugs)
                    .recommendations(recommendations)
                    .screenshots(screenshots)
                    .build();

            // ✅ Clean Save (Insert)
            TestResult savedResult = testRepository.save(result);

            System.out.println("💾 Test saved. Stream ID: " + streamId + " -> DB ID: " + savedResult.getId());
            pushLiveUpdates();

            // ✅ Send the SAVED result (which has the real DB ID) back to frontend
            sseService.sendResult(streamId, convertToDTO(savedResult));

        } catch (Exception e) {
            sseService.sendError(streamId, "Critical Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void pushLiveUpdates() {
        statsService.getStats();
        statsService.getTestTrends();
        statsService.getDistribution();
    }

    private boolean isValidImpact(String impact) {
        if (impact == null) return false;
        String lower = impact.toLowerCase();
        return lower.equals("low") || lower.equals("medium") || lower.equals("high");
    }

    private boolean isValidCategory(String category) {
        if (category == null) return false;
        String lower = category.toLowerCase();
        return lower.equals("performance") || lower.equals("security") ||
                lower.equals("accessibility") || lower.equals("seo") || lower.equals("ux");
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

    // ✅ Updated with Security Check (uses getCurrentUserId)
    public TestResultDTO getTestResult(String id) {
        String currentUserId = getCurrentUserId();

        TestResult test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found"));

        if (!test.getUserId().equals(currentUserId)) {
            throw new RuntimeException("Unauthorized: You cannot view this test.");
        }
        return convertToDTO(test);
    }

    // ✅ Updated with Security Check (uses getCurrentUserId)
    public List<TestResultDTO> getAllTestResults() {
        String currentUserId = getCurrentUserId();
        return testRepository.findByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    // ✅ Updated with Security Check (uses getCurrentUserId)
    public boolean deleteTestResult(String testId) {
        String currentUserId = getCurrentUserId();
        Optional<TestResult> testOpt = testRepository.findById(testId);

        if (testOpt.isPresent()) {
            TestResult test = testOpt.get();
            if (test.getUserId().equals(currentUserId)) {
                testRepository.deleteById(testId);
                return true;
            }
        }
        return false;
    }
}