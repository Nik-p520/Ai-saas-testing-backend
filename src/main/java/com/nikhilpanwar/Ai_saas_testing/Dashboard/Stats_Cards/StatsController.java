package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import java.util.List;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @GetMapping("/stats")
    public ResponseEntity<StatsDTO> getStats() {
        // Yeh method internally 'getCurrentUserId' use karta hai
        // isliye humein ID pass karne ki zaroorat nahi
        return ResponseEntity.ok(statsService.getStats());
    }

    // 2. Initial Trends Load
    @GetMapping("/trends")
    public ResponseEntity<List<TrendDTO>> getTrends() {
        return ResponseEntity.ok(statsService.getTestTrends());
    }

    // 3. Initial Distribution Load
    @GetMapping("/distribution")
    public ResponseEntity<List<DistributionDTO>> getDistribution() {
        return ResponseEntity.ok(statsService.getDistribution());
    }

    // 4. Initial Comparison Load
    @GetMapping("/comparisons")
    public ResponseEntity<Map<String, String>> getComparisons() {
        return ResponseEntity.ok(statsService.getComparisonData());
    }
}
