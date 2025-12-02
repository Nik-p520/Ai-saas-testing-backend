package com.nikhilpanwar.Ai_saas_testing.Dashboard.Stats_Cards;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DistributionDTO {

    private String status;
    private long count;

    public DistributionDTO() {
    }

    public DistributionDTO(String status, long count) {
        this.status = status;
        this.count = count;
    }

}
