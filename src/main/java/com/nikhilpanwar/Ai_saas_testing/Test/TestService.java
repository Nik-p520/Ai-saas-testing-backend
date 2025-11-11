package com.nikhilpanwar.Ai_saas_testing.Test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
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

    private static final String PYTHON_API_URL = "http://localhost:5000/generate-tests";
    private static final String PYTHON_EXECUTE_URL = "http://localhost:5000/execute-tests";

    // Folder to store screenshots locally (optional)
    private static final Path SCREENSHOT_DIR = Paths.get("uploads/screenshots");

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
                if (dto.isSuccess() && dto.getTest_script() != null) {
                    return dto.getTest_script();
                } else {
                    return "// ❌ AI generation failed: " + dto.getError();
                }
            } else {
                return "// ❌ AI service returned non-2xx response";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "// ⚠️ Error calling AI service: " + e.getMessage();
        }
    }

    /**
     * Generate script and execute it (FULL PIPELINE)
     */
    public TestResultDTO generateAndExecuteTest(TestRequestDTO requestDTO) {
        System.out.println("🚀 Starting test generation and execution for: " + requestDTO.getUrl());

        // 1️⃣ Generate the script
        String script = generateScript(requestDTO);
        if (script.startsWith("//")) {
            System.out.println("❌ Script generation failed");
            TestResult failed = TestResult.builder()
                    .websiteUrl(requestDTO.getUrl())
                    .status("failed")
                    .executionTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .script(script)
                    .logs(List.of("Script generation failed"))
                    .build();
            testRepository.save(failed);
            return convertToDTO(failed);
        }

        // 2️⃣ Execute the script
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

            // 🆕 Include URL for better AI context in recommendations
            Map<String, Object> execRequest = new HashMap<>();
            execRequest.put("test_script", script);
            execRequest.put("url", requestDTO.getUrl());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(execRequest, headers);

            System.out.println("📤 Calling Flask /execute-tests...");
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(PYTHON_EXECUTE_URL, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> execution = response.getBody();
                Boolean success = (Boolean) execution.get("success");

                // 🆕 Use Flask-provided status if available
                status = (String) execution.getOrDefault("status",
                        (success != null && success) ? "passed" : "failed");

                logs = extractStringList(execution, "logs");
                duration = (String) execution.getOrDefault("duration", "0s");
                browser = (String) execution.getOrDefault("browser", "chromium");

                // ✅ Extract structured bugs
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

                // ✅ Extract AI recommendations (FIXED: Use AI-generated IDs)
                List<Map<String, Object>> recList = (List<Map<String, Object>>) execution.get("recommendations");
                if (recList != null && !recList.isEmpty()) {
                    System.out.println("📋 Processing " + recList.size() + " AI recommendations");
                    for (Map<String, Object> r : recList) {
                        // 🔥 FIX: Use the recommendationId from AI, not generating new UUID
                        String recId = (String) r.get("recommendationId");
                        String title = (String) r.get("title");
                        String description = (String) r.get("description");
                        String impact = (String) r.get("impact");
                        String category = (String) r.get("category");

                        // Validate required fields
                        if (recId == null || recId.isEmpty()) {
                            recId = "rec_" + UUID.randomUUID().toString().substring(0, 12);
                        }
                        if (title == null || title.isEmpty()) {
                            title = "AI Recommendation";
                        }
                        if (description == null || description.isEmpty()) {
                            description = "No description provided";
                        }
                        if (impact == null || !isValidImpact(impact)) {
                            impact = "medium";
                        }
                        if (category == null || !isValidCategory(category)) {
                            category = "ux";
                        }

                        recommendations.add(TestResult.Recommendation.builder()
                                .recommendationId(recId)
                                .title(title)
                                .description(description)
                                .impact(impact.toLowerCase())
                                .category(category.toLowerCase())
                                .build());

                        System.out.println("  ✓ " + title + " [" + impact + "/" + category + "]");
                    }
                } else {
                    System.out.println("⚠️ No recommendations received from AI");
                }

                // 🆕 Extract screenshots (Base64)
                List<Map<String, Object>> shots = (List<Map<String, Object>>) execution.get("screenshots");
                if (shots != null && !shots.isEmpty()) {
                    Files.createDirectories(SCREENSHOT_DIR);
                    System.out.println("📸 Processing " + shots.size() + " screenshots");
                    for (Map<String, Object> shot : shots) {
                        String filename = (String) shot.getOrDefault("filename", UUID.randomUUID() + ".png");
                        String b64 = (String) shot.get("b64");
                        if (b64 != null) {
                            try {
                                byte[] bytes = Base64.getDecoder().decode(b64);
                                Path filePath = SCREENSHOT_DIR.resolve(filename);
                                Files.write(filePath, bytes);
                                String fileUrl = "/uploads/screenshots/" + filename; // frontend route
                                screenshots.add(TestResult.Screenshot.builder()
                                        .url(fileUrl)
                                        .caption(filename)
                                        .build());
                                System.out.println("  ✓ Saved: " + filename);
                            } catch (Exception e) {
                                logs.add("⚠️ Failed to save screenshot: " + filename);
                                System.err.println("Screenshot save error: " + e.getMessage());
                            }
                        }
                    }
                }

                if ("failed".equals(status) && bugs.isEmpty()) {
                    bugs.add(TestResult.BugItem.builder()
                            .bugId(UUID.randomUUID().toString())
                            .title("General Failure")
                            .description((String) execution.getOrDefault("error", "Unknown Error"))
                            .severity("high")
                            .build());
                }

            } else {
                logs.add("❌ Flask returned non-2xx: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logs.add("❌ Exception: " + e.getMessage());
            e.printStackTrace();
        }

        // 3️⃣ Save result in DB
        TestResult result = TestResult.builder()
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

        testRepository.save(result);
        System.out.println("💾 Test saved with ID: " + result.getId());
        System.out.println("   - Status: " + status);
        System.out.println("   - Bugs: " + bugs.size());
        System.out.println("   - Recommendations: " + recommendations.size());
        System.out.println("   - Screenshots: " + screenshots.size());

        return convertToDTO(result);
    }

    /**
     * Validates if impact is one of: low, medium, high
     */
    private boolean isValidImpact(String impact) {
        if (impact == null) return false;
        String lower = impact.toLowerCase();
        return lower.equals("low") || lower.equals("medium") || lower.equals("high");
    }

    /**
     * Validates if category is one of: performance, security, accessibility, seo, ux
     */
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

    public TestResultDTO getTestResult(String id) {
        return testRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Test not found"));
    }

    public List<TestResultDTO> getAllTestResults() {
        return testRepository.findAll().stream().map(this::convertToDTO).toList();
    }

    public boolean deleteTestResult(String testId) {
        if (testRepository.existsById(testId)) {
            testRepository.deleteById(testId);
            return true;
        }
        return false;
    }
}