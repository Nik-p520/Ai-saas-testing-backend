package com.nikhilpanwar.Ai_saas_testing.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            // Check karte hain ki pehle se initialized to nahi hai
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(
                                new ClassPathResource("service-account.json").getInputStream()))
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase Backend se connect ho gaya!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
