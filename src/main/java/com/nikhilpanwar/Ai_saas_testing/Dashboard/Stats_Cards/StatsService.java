package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

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
        long totalTests = testResultRepository.count();
        long passedTests = testResultRepository.countPassedTests();
        long activeTests = testResultRepository.countActiveTests();

        double totalSeconds = testResultRepository.findAll()
                .stream()
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

        ws.broadcastStats(dto);

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
    // Using double for totalTests to ensure precision during division
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
        LocalDate now = LocalDate.now();

        // 1. Current Window: Last 30 Days (e.g., Nov 1 - Nov 30)
        LocalDate currentStart = now.minusDays(29);
        LocalDate currentEnd = now;

        // 2. Previous Window: The 30 days prior (e.g., Oct 2 - Oct 31)
        LocalDate prevStart = now.minusDays(59);
        LocalDate prevEnd = now.minusDays(30);

        StatsPeriod current = calculatePeriodStats(currentStart, currentEnd);
        StatsPeriod previous = calculatePeriodStats(prevStart, prevEnd);

        Map<String, String> comparisons = new HashMap<>();

        // --- 1. Total Tests (% Change) ---
        // Unit: "Tests". Output: "+10.0% Tests" (Green) or "-5.0% Tests" (Red)
        comparisons.put("totalTests", formatComparison(
                current.totalTests,
                previous.totalTests,
                false, "Tests"));

        // --- 2. Avg Test Time (Inverted) ---
        // Unit: "s faster". Output: "+1.2s faster" (Green) or "-2.0s slower" (Red)
        comparisons.put("averageTime", formatComparison(
                current.averageTime,
                previous.averageTime,
                true, "s faster"));

        // --- 3. Success Rate (% Change) ---
        // Unit: "better". Output: "+5.0% better" (Green) or "-4.0% worse" (Red)
        comparisons.put("successRate", formatComparison(
                current.successRate,
                previous.successRate,
                false, "better"));

        ws.broadcastComparisons(comparisons);

        return comparisons;
    }

    /**
     * Fetches and calculates the stats for a specific time period.
     */
    private StatsPeriod calculatePeriodStats(LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        long total = testResultRepository.countTestsBetween(startDateTime, endDateTime);
        long passed = testResultRepository.countPassedTestsBetween(startDateTime, endDateTime);

        List<String> durations = testResultRepository.findDurationsBetween(startDateTime, endDateTime);

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

    /**
     * Formats the comparison string.
     * Uses concatenation to strictly control spaces and signs.
     */
    private String formatComparison(double current, double previous, boolean isAvgTime, String unit) {

        // Handle no data history
        if (previous == 0 || previous == 0.0) {
            return (current > 0) ? "New Data" : "No Change";
        }

        String sign;
        String description;
        double valueToFormat;

        if (isAvgTime) {
            // --- AVG TEST TIME LOGIC (Inverted) ---
            // Formula: Previous - Current
            // Example 1: Prev(10s) - Curr(8s) = +2. (+ means Faster/Green)
            // Example 2: Prev(10s) - Curr(12s) = -2. (- means Slower/Red)

            double difference = previous - current;

            sign = difference >= 0 ? "+" : "-";

            // If Positive (Faster), use unit ("s faster").
            // If Negative (Slower), use "s slower".
            description = difference >= 0 ? unit : "s slower";

            valueToFormat = Math.abs(difference);

            // Output: +2.5s faster OR -1.2s slower
            return sign + String.format("%.1f", valueToFormat) + description;

        } else {
            // --- TOTAL TESTS & SUCCESS RATE LOGIC (Standard) ---

            double percentageChange = ((current - previous) / previous) * 100;

            sign = percentageChange >= 0 ? "+" : "-";
            valueToFormat = Math.abs(percentageChange);

            if (unit.equals("Tests")) {
                // For Total Tests, we just show % Tests (Direction is handled by sign/color)
                description = "% " + unit;
            } else {
                // For Success Rate: "better" vs "worse"
                description = percentageChange >= 0
                        ? "% " + unit    // e.g. % better
                        : "% worse";     // e.g. % worse
            }

            // Output: +12.5% Tests OR -4.2% worse
            return sign + String.format("%.1f", valueToFormat) + description;
        }
    }


    // ============================
    // 📌 TRENDS (Last 7 days)
    // ============================
    public List<TrendDTO> getTestTrends() {
        List<Object[]> results = testResultRepository.countTestsByDay();

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

        ws.broadcastTrends(finalList);

        return finalList;
    }


    // ============================
    // 📌 DISTRIBUTION (Pie chart)
    // ============================
    public List<DistributionDTO> getDistribution() {
        long passed = testResultRepository.countPassedTests();
        long failed = testResultRepository.countFailedTests();
        long processing = testResultRepository.countActiveTests();

        List<DistributionDTO> dtoList = Arrays.asList(
                new DistributionDTO("Passed", passed),
                new DistributionDTO("Failed", failed),
                new DistributionDTO("Processing", processing)
        );

        ws.broadcastDistribution(dtoList);

        return dtoList;
    }
}