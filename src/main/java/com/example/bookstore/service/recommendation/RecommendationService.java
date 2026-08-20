package com.example.bookstore.service.recommendation;

import com.example.bookstore.config.RecommendationConfig;
import com.example.bookstore.model.AssociationRule;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.AssociationRuleRepository;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.service.recommendation.cosine.CosineSimilarityAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RecommendationService: API layer for book recommendations.
 * 
 * Changes from in-memory cache approach:
 * - getBoughtTogetherBooks(): Query database association_rules table
 * - getSimilarBooks(): Use CosineSimilarityAlgorithm on books in same category
 * - Removed dependency on RecommendationCacheHolder (no more in-memory state)
 * - Added fallback engine for graceful degradation
 */
@Service
public class RecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);
    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.valueOf(0.3);  // 30% minimum

    @Autowired
    private RecommendationConfig config;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AssociationRuleRepository associationRuleRepository;

    @Autowired
    private RecommendationFallbackEngine fallbackEngine;

    @Autowired
    private CosineSimilarityAlgorithm cosineAlgorithm;

    @Autowired
    private RecommendationRedisService redisService;

    /**
     * Get "bought together" recommendations from mined association rules.
     * 
     * Algorithm:
     * 1. Query database for rules where bookA = given bookId
     * 2. Filter by confidence >= 30% and lift > 1.0
     * 3. Return top N books sorted by confidence DESC, lift DESC
     * 4. If insufficient results, fallback to same author/category
     * 
     * @param bookId Source book ID
     * @return List of recommended books (up to maxBoughtTogether)
     */
    public List<Book> getBoughtTogetherBooks(Long bookId) {
        logger.debug("[RecommendationService] Bắt đầu lấy gợi ý Lambda (Redis + FP-Growth DB) cho Sách {}", bookId);

        List<Book> result = new ArrayList<>();
        Set<Long> addedBookIds = new LinkedHashSet<>(); // Giữ thứ tự và chống trùng
        int maxResults = config.getMaxBoughtTogether();

        try {
            // LAYER 1: SPEED LAYER (Lấy từ REDIS - Real-time)
            List<Long> realTimeIds = redisService.getRealTimeRecommendations(bookId, maxResults);
            for (Long id : realTimeIds) {
                if (addedBookIds.size() >= maxResults) break;
                Optional<Book> b = bookRepository.findById(id);
                if (b.isPresent() && b.get().getApprovalStatus() == ApprovalStatus.APPROVED) {
                    result.add(b.get());
                    addedBookIds.add(id);
                }
            }
            logger.debug("Lấy được {} sách nóng từ Redis", result.size());

            // LAYER 2: BATCH LAYER (Lấy từ DB do FP-Growth tính ra)
            if (result.size() < maxResults) {
                List<AssociationRule> dbRules = associationRuleRepository.findBoughtTogetherByBookId(bookId, MIN_CONFIDENCE);
                for (AssociationRule rule : dbRules) {
                    if (addedBookIds.size() >= maxResults) break;
                    Book bookB = rule.getBookB();

                    // Chỉ thêm nếu chưa có (tránh trùng với sách bên Redis đã lấy)
                    if (bookB.getApprovalStatus() == ApprovalStatus.APPROVED && !addedBookIds.contains(bookB.getId())) {
                        result.add(bookB);
                        addedBookIds.add(bookB.getId());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("[RecommendationService] Lỗi khi xử lý Lambda Architecture", e);
        }

        // LAYER 3: FALLBACK (Nếu vẫn không đủ số lượng, lấy sách cùng tác giả/thể loại)
        if (result.size() < maxResults) {
            Optional<Book> currentBookOpt = bookRepository.findById(bookId);
            if (currentBookOpt.isPresent()) {
                int needed = maxResults - result.size();
                List<Book> fallback = fallbackEngine.fallbackSameAuthorOrCategory(currentBookOpt.get(), needed, result);
                result.addAll(fallback);
            }
        }

        return result;
    }
    /**
     * Get "similar books" recommendations using CosineSimilarity algorithm.
     * 
     * Algorithm:
     * 1. Fetch source book from database
     * 2. Get all approved books in same category
     * 3. Calculate cosine similarity (author + category + text TF-IDF)
     * 4. Return top N by similarity score
     * 5. If insufficient, fallback to same author/category
     * 
     * @param bookId Source book ID
     * @return List of similar books (up to maxSimilar)
     */
    public List<Book> getSimilarBooks(Long bookId) {
        logger.debug("[RecommendationService] Getting 'similar books' for bookId={}", bookId);
        
        List<Book> result = new ArrayList<>();
        
        try {
            // Step 1: Fetch source book
            Optional<Book> sourceBookOpt = bookRepository.findById(bookId);
            if (sourceBookOpt.isEmpty()) {
                logger.warn("[RecommendationService] Source book {} not found", bookId);
                return result;
            }
            
            Book sourceBook = sourceBookOpt.get();

            List<Book> candidates = new ArrayList<>();
            if (sourceBook.getCategory() != null) {
                //  Gắn thêm Sort để 50 ứng viên này là những cuốn sách mới nhất
                Pageable topCandidates = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));

                // (Hoặc nếu hệ thống có trường số lượng bán, lấy 50 cuốn bán chạy nhất làm ứng viên)
                // Pageable topCandidates = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "soldQuantity"));

                candidates = bookRepository.findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
                        sourceBook.getCategory().getId(),
                        bookId,
                        ApprovalStatus.APPROVED,
                        topCandidates
                );
            }
            
            // Step 3: Score each candidate using cosine similarity
            Map<Book, Double> scoredBooks = new HashMap<>();
            for (Book candidate : candidates) {
                double similarity = cosineAlgorithm.calculateSimilarity(sourceBook, candidate);
                scoredBooks.put(candidate, similarity);
                logger.trace("[RecommendationService] Similarity({}->{}): {}", 
                    bookId, candidate.getId(), similarity);
            }
            
            // Step 4: Sort by similarity score DESC and take top N
            result = scoredBooks.entrySet().stream()
                .filter(e -> e.getValue() > 0.0)  // Only include positive scores
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(config.getMaxSimilar())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            logger.info("[RecommendationService] Cosine similarity returned {} books for bookId={}", 
                result.size(), bookId);
            
        } catch (Exception e) {
            logger.error("[RecommendationService] Error calculating similar books for bookId={}", bookId, e);
        }

        // Step 5: Fallback if insufficient results
        if (result.size() < config.getMaxSimilar()) {
            Optional<Book> currentBookOpt = bookRepository.findById(bookId);
            if (currentBookOpt.isPresent()) {
                int needed = config.getMaxSimilar() - result.size();
                logger.debug("[RecommendationService] Fallback: need {} more similar books", needed);
                
                List<Book> fallback = fallbackEngine.fallbackSameAuthorOrCategory(
                    currentBookOpt.get(), 
                    needed, 
                    result
                );
                result.addAll(fallback);
                logger.info("[RecommendationService] After fallback: {} similar books", result.size());
            }
        }

        return result;
    }

}
