package com.example.bookstore;

import com.example.bookstore.config.JwtUtil;
import com.example.bookstore.dto.SeedRequest;
import com.example.bookstore.dto.SeedResult;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.service.DatabaseSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DatabaseSeederService databaseSeederService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.seeder.ai-enabled:false}")
    private boolean aiEnabled;

    @Value("${app.seeder.max-books:300}")
    private int maxBooks;

    @Override
    public void run(String... args) {
        SeedRequest request = new SeedRequest();
        request.setIncludeAi(aiEnabled);
        request.setMaxBooks(maxBooks);
        SeedResult result = databaseSeederService.seedData(request);

        // Generate and log JWT tokens for seeded users
        logTokensForSeededUsers();
    }

    /**
     * Generate JWT tokens for known seed accounts and log them to console.
     * This allows developers to immediately use these tokens without OTP login.
     */
    private void logTokensForSeededUsers() {
        List<String> seedEmails = List.of(
            "admin@gmail.com",
            "shop_nha_nam@gmail.com",
            "shop_tre@gmail.com"
        );

        for (String email : seedEmails) {
            try {
                User user = userRepository.findByUsername(email);
                if (user == null) {
                    log.warn("Seed user not found: {}", email);
                    continue;
                }

                String token = jwtUtil.generateToken(user);
                String roleLabel = user.getRole() != null ? user.getRole().name() : "UNKNOWN";

                log.info("========================================");
                log.info("TOKEN for {} ({})", email, roleLabel);
                log.info("{}", token);
                log.info("========================================");
            } catch (Exception e) {
                log.warn("Failed to generate token for {}: {}", email, e.getMessage());
            }
        }
    }
}
