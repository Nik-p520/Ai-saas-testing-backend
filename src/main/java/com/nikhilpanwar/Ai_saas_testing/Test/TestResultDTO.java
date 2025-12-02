package com.nikhilpanwar.Ai_saas_testing.Test;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestResultDTO {

    private String id;
    private String websiteUrl;
    private LocalDateTime executionTime;
    private String duration; // "5s", "3m 20s"
    private String browser; // "chromium", "firefox", "webkit"
    private String status; // "passed", "failed", "processing"
    private List<String> logs;
    private List<Screenshot> screenshots;
    private String script;
    private List<BugItem> bugs;
    private List<Recommendation> recommendations;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // ==================== NESTED CLASSES ====================

    /**
     * Screenshot DTO
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Screenshot {
        private String url;
        private String caption;
    }

    /**
     * Bug Item DTO
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BugItem {
        private String bugId;
        private String title;
        private String description;
        private String severity; // "low", "medium", "high", "critical"
    }

    /**
     * Recommendation DTO
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Recommendation {
        private String recommendationId;
        private String title;
        private String description;
        private String impact; // "low", "medium", "high"
        private String category; // "performance", "security", "accessibility", "seo", "ux"
    }
}
