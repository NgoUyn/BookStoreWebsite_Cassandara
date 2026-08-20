package com.example.bookstore.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class GeminiService {
    @Value("${google.gemini.api-key:MISSING_KEY}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank() && !"MISSING_KEY".equals(apiKey);
    }

    // Dung Bat dong bo (@Async) de viec goi AI khong lam dung server
    @Async
    public CompletableFuture<String> generateDescription(String title,String author){
        if(!isEnabled()) return CompletableFuture.completedFuture(null);

        String geminiUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        String prompt = "Viết một đoạn mô tả ngắn gọn, hấp dẫn, gợi mở được sự hứng thú của người xem bằng tiếng Việt (khoảng 3-4 câu) về cuốn sách '"
                + title + "' của " + author + ". Khong dung markdown.";

        try{
            Map<String,Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts",List.of(Map.of("text",prompt))))
            );
            JsonNode response = restClient.post()
                    .uri(geminiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response.at("/candidates/0/content/parts/0/text").asText();
            return CompletableFuture.completedFuture((text.trim()));
        }catch(Exception e){
            return CompletableFuture.completedFuture(null);
        }
    }
}
