package com.example.bookstore.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {

    // Đây là giá trị được inject từ application.properties
    @Value("${firebase.config.path}")
    private Resource firebaseResource;

    @PostConstruct
    public void checkEnvVariable() {
        System.out.println("=========================================");
        System.out.println("DEBUG FIREBASE CONFIG:");
        // In ra mô tả của đối tượng Resource để xem Spring đang trỏ vào đâu
        System.out.println("👉 Resource description: " + firebaseResource.getDescription());
        System.out.println("👉 Resource exists: " + firebaseResource.exists());
        System.out.println("=========================================");
    }

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream serviceAccount = firebaseResource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                return FirebaseApp.initializeApp(options);
            }
        }
        return FirebaseApp.getInstance();
    }
}