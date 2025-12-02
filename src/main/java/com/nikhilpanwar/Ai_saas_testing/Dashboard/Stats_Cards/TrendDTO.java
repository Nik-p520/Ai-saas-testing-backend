package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrendDTO {
    private String day;   // Mon, Tue, Wed...
    private long count;   // Number of tests on that day
}
