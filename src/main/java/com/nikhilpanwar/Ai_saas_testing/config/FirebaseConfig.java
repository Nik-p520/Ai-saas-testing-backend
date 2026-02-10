package com.nikhilpanwar.Ai_saas_testing.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct; // Check if using Spring Boot 3

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                // Render ke Environment Variables se JSON string uthayega
                String firebaseConfig = System.getenv("FIREBASE_CONFIG_JSON");

                if (firebaseConfig != null && !firebaseConfig.isEmpty()) {
                    InputStream serviceAccount = new ByteArrayInputStream(firebaseConfig.getBytes());
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                    FirebaseApp.initializeApp(options);
                    System.out.println("✅ Firebase Admin SDK Initialized from Environment Variable!");
                } else {
                    System.out.println("⚠️ ERROR: FIREBASE_CONFIG_JSON variable not found on Render!");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Firebase Init Error: " + e.getMessage());
        }
    }
}