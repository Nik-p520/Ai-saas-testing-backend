package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardPublisher {

    private final StatsService statsService;

    // 1. Schedule for current, fast-moving stats and distribution (e.g., every 5 seconds)
    // These methods internally call ws.broadcastX()
    @Scheduled(fixedRateString = "${dashboard.stats.update-rate:5000}")
    public void publishCurrentStats() {
        statsService.getStats();
    }

    // 2. Schedule for the Test Trends (often updated at the same rate as stats)
    @Scheduled(fixedRateString = "${dashboard.trends.update-rate:5000}")
    public void publishTestTrends() {
        statsService.getTestTrends();
    }

    // 3. Schedule for the Distribution data (often updated at the same rate as stats)
    @Scheduled(fixedRateString = "${dashboard.distribution.update-rate:5000}")
    public void publishDistribution() {
        statsService.getDistribution();
    }

    // 4. 🔥 NEW: Schedule for historical comparison data (Slower, e.g., every 10 minutes)
    @Scheduled(fixedRateString = "${dashboard.comparison.update-rate:600000}")
    public void publishComparisonData() {
        // This calls statsService.getComparisonData(), which internally broadcasts the result
        statsService.getComparisonData();
    }
}