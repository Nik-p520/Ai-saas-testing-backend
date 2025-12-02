package com.nikhilpanwar.Ai_saas_testing.Test;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "test_results")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 2048)
    private String websiteUrl;

    @Column(nullable = false)
    private LocalDateTime executionTime;

    private String duration; // "5s", "3m 20s"

    private String browser; // "chromium", "firefox", "webkit"

    @Column(nullable = false, length = 20)
    private String status; // "passed", "failed", "processing"

    @ElementCollection
    @CollectionTable(name = "test_logs", joinColumns = @JoinColumn(name = "test_result_id"))
    @Column(name = "log", columnDefinition = "TEXT")
    private List<String> logs;

    @ElementCollection
    @CollectionTable(name = "test_screenshots", joinColumns = @JoinColumn(name = "test_result_id"))
    private List<Screenshot> screenshots;

    @Column(columnDefinition = "TEXT")
    private String script; // Generated Playwright script

    @ElementCollection
    @CollectionTable(name = "test_bugs", joinColumns = @JoinColumn(name = "test_result_id"))
    private List<BugItem> bugs;

    @ElementCollection
    @CollectionTable(name = "test_recommendations", joinColumns = @JoinColumn(name = "test_result_id"))
    private List<Recommendation> recommendations;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // ==================== EMBEDDED CLASSES ====================

    /**
     * Screenshot embedded class
     */
    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Screenshot {

        @Column(nullable = false)
        private String url;

        private String caption;
    }

    /**
     * Bug embedded class
     */
    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BugItem {

        @Column(nullable = false)
        private String bugId; // Using bugId to avoid conflict with table id

        @Column(nullable = false)
        private String title;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String description;

        @Column(nullable = false, length = 20)
        private String severity; // "low", "medium", "high", "critical"
    }

    /**
     * Recommendation embedded class
     */
    @Embeddable
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Recommendation {

        @Column(nullable = false)
        private String recommendationId; // Using recommendationId to avoid conflict with table id

        @Column(nullable = false)
        private String title;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String description;

        @Column(nullable = false, length = 20)
        private String impact; // "low", "medium", "high"

        @Column(nullable = false)
        private String category; // "performance", "security", "accessibility", "seo", "ux"
    }
}