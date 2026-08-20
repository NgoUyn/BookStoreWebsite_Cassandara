package com.example.bookstore.controller;

import com.example.bookstore.model.Book;
import com.example.bookstore.service.recommendation.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    // API lấy Sách thường mua kèm
    @GetMapping("/{bookId}/bought-together")
    public ResponseEntity<List<Book>> getBoughtTogether(@PathVariable Long bookId) {
        List<Book> recommendations = recommendationService.getBoughtTogetherBooks(bookId);
        return ResponseEntity.ok(recommendations);
    }

    // Tiện thể mở luôn API lấy Sách tương tự (Cosine Similarity)
    @GetMapping("/{bookId}/similar")
    public ResponseEntity<List<Book>> getSimilarBooks(@PathVariable Long bookId) {
        List<Book> similar = recommendationService.getSimilarBooks(bookId);
        return ResponseEntity.ok(similar);
    }
}