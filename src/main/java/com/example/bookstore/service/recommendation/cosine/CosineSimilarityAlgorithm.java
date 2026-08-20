package com.example.bookstore.service.recommendation.cosine;

import com.example.bookstore.config.RecommendationConfig;
import com.example.bookstore.model.Book;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CosineSimilarityAlgorithm {

    private final RecommendationConfig config;

    public CosineSimilarityAlgorithm(RecommendationConfig config) {
        this.config = config;
    }

    /**
     * Tính độ tương đồng giữa hai cuốn sách dựa trên Tác giả, Thể loại và Nội dung (Tiêu đề + Mô tả)
     */
    public double calculateSimilarity(Book bookA, Book bookB) {
        double score = 0.0;

        // 1. Tương đồng về tác giả (Author match)
        if (bookA.getAuthor() != null && bookB.getAuthor() != null 
            && bookA.getAuthor().equalsIgnoreCase(bookB.getAuthor())) {
            score += config.getAuthorWeight();
        }

        // 2. Tương đồng về thể loại (Category match)
        if (bookA.getCategory() != null && bookB.getCategory() != null 
            && Objects.equals(bookA.getCategory().getId(), bookB.getCategory().getId())) {
            score += config.getCategoryWeight();
        }

        // 3. Tương đồng về văn bản (Text - Bag of Words / Cosine)
        String textA = (bookA.getTitle() + " " + (bookA.getDescription() != null ? bookA.getDescription() : "")).toLowerCase();
        String textB = (bookB.getTitle() + " " + (bookB.getDescription() != null ? bookB.getDescription() : "")).toLowerCase();
        
        double textSim = calculateTextCosine(textA, textB);
        score += config.getTextWeight() * textSim;

        return score;
    }

    /**
     * Tính Cosine Similarity của 2 đoạn văn bản dựa trên tần suất (Term Frequency)
     */
    private double calculateTextCosine(String textA, String textB) {
        if (textA.isBlank() || textB.isBlank()) return 0.0;

        Map<String, Integer> freqA = getWordFrequencies(textA);
        Map<String, Integer> freqB = getWordFrequencies(textB);

        Set<String> allWords = new HashSet<>();
        allWords.addAll(freqA.keySet());
        allWords.addAll(freqB.keySet());

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (String word : allWords) {
            int countA = freqA.getOrDefault(word, 0);
            int countB = freqB.getOrDefault(word, 0);
            
            dotProduct += countA * countB;
            normA += countA * countA;
            normB += countB * countB;
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Tách từ và lập bảng tần suất (Bỏ qua ký tự đặc biệt, chỉ lấy chữ và số)
     */
    private Map<String, Integer> getWordFrequencies(String text) {
        Map<String, Integer> freqs = new HashMap<>();
        // Regex: Giữ lại letters (\p{L}) và numbers (\p{Nd}), thay thế còn lại bằng khoảng trắng
        String[] words = text.replaceAll("[^\\p{L}\\p{Nd}]+", " ").split("\\s+");
        for (String word : words) {
            if (!word.isBlank() && word.length() > 1) { // Bỏ qua từ quá ngắn
                freqs.put(word, freqs.getOrDefault(word, 0) + 1);
            }
        }
        return freqs;
    }
}
