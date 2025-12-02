package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatsDTO {
    private long totalTests;
    private double averageTime; // in seconds
    private double successRate;     // %
    private long activeTests;
}
