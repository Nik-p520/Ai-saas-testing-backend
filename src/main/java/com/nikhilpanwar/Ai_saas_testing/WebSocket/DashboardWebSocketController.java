package com.nikhilpanwar.Ai_saas_testing.WebSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DashboardWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    // ---------------------------------------------------------
    // ✅ NEW STRATEGY: DYNAMIC TOPICS
    // ---------------------------------------------------------

    public void sendStatsToUser(String userId, Object stats) {
        if (userId != null) {
            // Address ban jayega: /topic/stats/AbCdEf12345
            messagingTemplate.convertAndSend("/topic/stats/" + userId, stats);
        }
    }

    public void sendTrendsToUser(String userId, Object trends) {
        if (userId != null) {
            messagingTemplate.convertAndSend("/topic/trends/" + userId, trends);
        }
    }

    public void sendDistributionToUser(String userId, Object distribution) {
        if (userId != null) {
            messagingTemplate.convertAndSend("/topic/distribution/" + userId, distribution);
        }
    }

    public void sendComparisonsToUser(String userId, Object comparisonData) {
        if (userId != null) {
            messagingTemplate.convertAndSend("/topic/comparisons/" + userId, comparisonData);
        }
    }
}