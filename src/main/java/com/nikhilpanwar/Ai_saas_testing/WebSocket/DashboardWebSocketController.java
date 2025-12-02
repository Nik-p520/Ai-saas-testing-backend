package com.nikhilpanwar.Ai_saas_testing.WebSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller; // Changed from @Component to @Controller
import java.util.Map; // Required for the broadcastComparisons method

@Controller // Using @Controller is standard for WebSocket message handlers
@RequiredArgsConstructor
public class DashboardWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastStats(Object stats) {
        messagingTemplate.convertAndSend("/topic/stats", stats);
    }

    public void broadcastTrends(Object trends) {
        messagingTemplate.convertAndSend("/topic/trends", trends);
    }

    public void broadcastDistribution(Object distribution) {
        messagingTemplate.convertAndSend("/topic/distribution", distribution);
    }

    /**
     * ✅ FIX: This method resolves the "Cannot resolve method 'broadcastComparisons'" error.
     * It broadcasts the comparison data (Map<String, String>) to a new WebSocket topic.
     */
    public void broadcastComparisons(Object comparisonData) {
        // You should define a unique topic for the comparison data
        messagingTemplate.convertAndSend("/topic/comparisons", comparisonData);
    }
}