package com.example.bookstore.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Component
public class JwtTokenProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final long expirationSeconds;

    public JwtTokenProvider(
        @Value("${app.security.jwt.secret:bookstore-dev-secret-change-me}") String secret,
        @Value("${app.security.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String role) {
        return createToken(userId, List.of(role), "SELLER".equalsIgnoreCase(role) ? userId : null);
    }

    public String createToken(Long userId, List<String> roles, Long sellerId) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expirationSeconds;

        Long normalizedSellerId = sellerId;
        if (normalizedSellerId == null && roles != null && roles.stream().filter(Objects::nonNull).anyMatch(role -> "SELLER".equalsIgnoreCase(role))) {
            normalizedSellerId = userId;
        }

        ObjectNode payloadNode = OBJECT_MAPPER.createObjectNode();
        payloadNode.put("userId", userId);
        payloadNode.putPOJO("roles", roles == null ? List.of() : roles);
        if (normalizedSellerId == null) {
            payloadNode.putNull("sellerId");
        } else {
            payloadNode.put("sellerId", normalizedSellerId);
        }
        payloadNode.put("iat", now);
        payloadNode.put("exp", exp);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload;
        try {
            payload = base64UrlEncode(OBJECT_MAPPER.writeValueAsBytes(payloadNode));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize JWT payload", ex);
        }
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    public Long extractUserId(String token) {
        JsonNode payload = validateAndParse(token);
        if (payload == null || !payload.has("userId")) {
            return null;
        }
        return payload.get("userId").asLong();
    }

    public List<String> extractRoles(String token) {
        JsonNode payload = validateAndParse(token);
        if (payload == null) {
            return List.of();
        }

        JsonNode rolesNode = payload.get("roles");
        if (rolesNode != null && rolesNode.isArray()) {
            List<String> roles = new java.util.ArrayList<>();
            for (JsonNode roleNode : rolesNode) {
                if (roleNode != null && !roleNode.isNull()) {
                    roles.add(roleNode.asText());
                }
            }
            if (!roles.isEmpty()) {
                return roles;
            }
        }

        if (payload.hasNonNull("role")) {
            return List.of(payload.get("role").asText());
        }

        return List.of();
    }

    public Long extractSellerId(String token) {
        JsonNode payload = validateAndParse(token);
        if (payload == null) {
            return null;
        }

        if (payload.hasNonNull("sellerId")) {
            return payload.get("sellerId").asLong();
        }

        Long userId = extractUserId(token);
        if (userId == null) {
            return null;
        }

        List<String> roles = extractRoles(token);
        boolean sellerRole = roles.stream().anyMatch(role -> "SELLER".equalsIgnoreCase(role));
        return sellerRole ? userId : null;
    }

    private JsonNode validateAndParse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = sign(signingInput);
            if (!constantTimeEquals(expectedSignature, parts[2])) {
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(payloadBytes);

            if (!payload.has("exp")) {
                return null;
            }

            long exp = payload.get("exp").asLong();
            long now = Instant.now().getEpochSecond();
            if (exp < now) {
                return null;
            }

            return payload;
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String input) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] signature = hmac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign JWT", ex);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
