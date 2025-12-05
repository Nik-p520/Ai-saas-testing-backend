package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResult;
import com.nikhilpanwar.Ai_saas_testing.Test.TestResultRepository;
import com.nikhilpanwar.Ai_saas_testing.WebSocket.DashboardWebSocketController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class StatsService {

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired
    private DashboardWebSocketController ws;

    // Helper to get current Firebase UID
    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName(); // Ye Firebase UID hai
        }
        return "Anonymous";
    }

    // ============================
    // 📌 STATS (Current Snapshot)
    // ============================
    public StatsDTO getStats() {
        String userId = getCurrentUserId(); // 1. User ID nikala

        // 2. Sirf user ka data fetch kiya (findAll ki jagah)
        List<TestResult> userTests = testResultRepository.findByUserIdOrderByCreatedAtDesc(userId);

        long totalTests = userTests.size(); // Count user ke list se nikala
        long passedTests = testResultRepository.countPassedTests(userId);
        long activeTests = testResultRepository.countActiveTests(userId);

        // Calculate average time using only User's data
        double totalSeconds = userTests.stream()
                .mapToDouble(test -> convertDurationToSeconds(test.getDuration()))
                .sum();

        double averageTime = passedTests > 0 ? totalSeconds / passedTests : 0;
        double successRate = totalTests > 0 ? (passedTests * 100.0) / totalTests : 0;

        StatsDTO dto = new StatsDTO(
                totalTests,
                averageTime,
                successRate,
                activeTests
        );

        // ✅ FIX: Broadcast ki jagah sirf user ko send karo
        ws.sendStatsToUser(userId, dto);

        return dto;
    }

    private double convertDurationToSeconds(String duration) {
        if (duration == null || duration.trim().isEmpty()) return 0;
        duration = duration.toLowerCase().trim();

        if (duration.endsWith("ms")) {
            try {
                return Double.parseDouble(duration.replace("ms", "").trim()) / 1000.0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        double totalSeconds = 0;
        Pattern minPattern = Pattern.compile("(\\d+)\\s*m");
        Matcher minMatcher = minPattern.matcher(duration);
        if (minMatcher.find()) {
            totalSeconds += Integer.parseInt(minMatcher.group(1)) * 60;
        }

        Pattern secPattern = Pattern.compile("([0-9.]+)\\s*s");
        Matcher secMatcher = secPattern.matcher(duration);
        if (secMatcher.find()) {
            totalSeconds += Double.parseDouble(secMatcher.group(1));
        }

        if (totalSeconds == 0 && duration.matches("[0-9.]+")) {
            totalSeconds = Double.parseDouble(duration);
        }

        return totalSeconds;
    }

    // --- Helper classes for comparison logic ---
    private static class StatsPeriod {
        double totalTests;
        double averageTime;
        double successRate;

        public StatsPeriod(double t, double avg, double s) {
            this.totalTests = t;
            this.averageTime = avg;
            this.successRate = s;
        }
    }

    // ============================
    // 📌 COMPARISON LOGIC (Month vs Previous Month)
    // ============================
    public Map<String, String> getComparisonData() {
        String userId = getCurrentUserId(); // User ID needed for comparisons
        LocalDate now = LocalDate.now();

        // 1. Current Window: Last 30 Days
        LocalDate currentStart = now.minusDays(29);
        LocalDate currentEnd = now;

        // 2. Previous Window: The 30 days prior
        LocalDate prevStart = now.minusDays(59);
        LocalDate prevEnd = now.minusDays(30);

        // Pass userId to the calculation method
        StatsPeriod current = calculatePeriodStats(userId, currentStart, currentEnd);
        StatsPeriod previous = calculatePeriodStats(userId, prevStart, prevEnd);

        Map<String, String> comparisons = new HashMap<>();

        comparisons.put("totalTests", formatComparison(
                current.totalTests,
                previous.totalTests,
                false, "Tests"));

        comparisons.put("averageTime", formatComparison(
                current.averageTime,
                previous.averageTime,
                true, "s faster"));

        comparisons.put("successRate", formatComparison(
                current.successRate,
                previous.successRate,
                false, "better"));

        // ✅ FIX: Broadcast ki jagah sirf user ko send karo
        ws.sendComparisonsToUser(userId, comparisons);

        return comparisons;
    }

    /**
     * Fetches and calculates the stats for a specific time period AND User.
     */
    private StatsPeriod calculatePeriodStats(String userId, LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        // Updated Repository Calls with userId
        long total = testResultRepository.countTestsBetween(userId, startDateTime, endDateTime);
        long passed = testResultRepository.countPassedTestsBetween(userId, startDateTime, endDateTime);

        List<String> durations = testResultRepository.findDurationsBetween(userId, startDateTime, endDateTime);

        if (durations == null) {
            durations = Collections.emptyList();
        }

        double totalDuration = durations.stream()
                .mapToDouble(this::convertDurationToSeconds)
                .sum();

        double avg = passed > 0 ? totalDuration / passed : 0;
        double success = total > 0 ? (passed * 100.0) / total : 0;

        return new StatsPeriod((double) total, avg, success);
    }

    private String formatComparison(double current, double previous, boolean isAvgTime, String unit) {
        if (previous == 0 || previous == 0.0) {
            return (current > 0) ? "New Data" : "No Change";
        }

        String sign;
        String description;
        double valueToFormat;

        if (isAvgTime) {
            double difference = previous - current;
            sign = difference >= 0 ? "+" : "-";
            description = difference >= 0 ? unit : "s slower";
            valueToFormat = Math.abs(difference);
            return sign + String.format("%.1f", valueToFormat) + description;

        } else {
            double percentageChange = ((current - previous) / previous) * 100;
            sign = percentageChange >= 0 ? "+" : "-";
            valueToFormat = Math.abs(percentageChange);

            if (unit.equals("Tests")) {
                description = "% " + unit;
            } else {
                description = percentageChange >= 0 ? "% " + unit : "% worse";
            }
            return sign + String.format("%.1f", valueToFormat) + description;
        }
    }


    // ============================
    // 📌 TRENDS (Last 7 days)
    // ============================
    public List<TrendDTO> getTestTrends() {
        String userId = getCurrentUserId(); // User ID

        // Pass userId to repo
        List<Object[]> results = testResultRepository.countTestsByDay(userId);

        Map<LocalDate, Long> trends = new HashMap<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            trends.put(date, 0L);
        }

        for (Object[] row : results) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            if (trends.containsKey(date)) {
                trends.put(date, (Long) row[1]);
            }
        }

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE");

        List<TrendDTO> finalList = trends.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrendDTO(
                        e.getKey().format(dayFmt),
                        e.getValue()
                ))
                .toList();

        // ✅ FIX: Broadcast ki jagah sirf user ko send karo
        ws.sendTrendsToUser(userId, finalList);

        return finalList;
    }


    // ============================
    // 📌 DISTRIBUTION (Pie chart)
    // ============================
    public List<DistributionDTO> getDistribution() {
        String userId = getCurrentUserId(); // User ID

        // Pass userId to all counts
        long passed = testResultRepository.countPassedTests(userId);
        long failed = testResultRepository.countFailedTests(userId);
        long processing = testResultRepository.countActiveTests(userId);

        List<DistributionDTO> dtoList = Arrays.asList(
                new DistributionDTO("Passed", passed),
                new DistributionDTO("Failed", failed),
                new DistributionDTO("Processing", processing)
        );

        // ✅ FIX: Broadcast ki jagah sirf user ko send karo
        ws.sendDistributionToUser(userId, dtoList);

        return dtoList;
    }
}