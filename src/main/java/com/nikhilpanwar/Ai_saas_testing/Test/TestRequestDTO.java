package com.nikhilpanwar.Ai_saas_testing.Test;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestRequestDTO {
    private String url;
    private Credentials credentials;
    private Map<String, Object> testRequirements; // Optional

    /**
     * Nested credentials class
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Credentials {
        private String username;
        private String password;
    }
}
