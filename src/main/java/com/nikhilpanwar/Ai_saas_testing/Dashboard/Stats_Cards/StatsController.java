package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import java.util.List;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @GetMapping("/stats")
    public StatsDTO getStats() {
        return statsService.getStats();
    }

    @GetMapping("/trends")
    public List<TrendDTO> getTestTrends() {
        return statsService.getTestTrends();
    }

    @GetMapping("/distribution")
    public List<DistributionDTO> getDistribution() {
        return statsService.getDistribution();
    }

    @GetMapping("/comparison")
    public Map<String, String> getComparisonData() {
        return statsService.getComparisonData();
    }
}
