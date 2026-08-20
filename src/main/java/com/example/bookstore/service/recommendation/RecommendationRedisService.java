package com.example.bookstore.service.recommendation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationRedisService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "realtime_bought_together:";

    // Tăng điểm co-occurrence cho cặp (bookA, bookB)
    public void incrementCoOccurrence(Long bookIdA, Long bookIdB) {
        String keyA = REDIS_KEY_PREFIX + bookIdA;
        String keyB = REDIS_KEY_PREFIX + bookIdB;

        // Tăng điểm trong Sorted Set (ZSET)
        redisTemplate.opsForZSet().incrementScore(keyA, String.valueOf(bookIdB), 1.0);
        redisTemplate.opsForZSet().incrementScore(keyB, String.valueOf(bookIdA), 1.0);
    }

    // Lấy top N sách được mua cùng nhiều nhất theo thời gian thực
    public List<Long> getRealTimeRecommendations(Long bookId, int limit) {
        String key = REDIS_KEY_PREFIX + bookId;
        // Lấy từ Score cao nhất xuống thấp nhất
        Set<String> topMatches = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

        if (topMatches == null || topMatches.isEmpty()) {
            return Collections.emptyList();
        }

        return topMatches.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}