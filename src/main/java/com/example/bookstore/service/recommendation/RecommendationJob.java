package com.example.bookstore.service.recommendation;

import com.example.bookstore.config.RecommendationConfig;
import com.example.bookstore.model.AssociationRule;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.repository.AssociationRuleRepository;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.OrderItemRepository;
import com.example.bookstore.service.recommendation.fpgrowth.FPGrowthAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RecommendationJob {
    private static final Logger logger = LoggerFactory.getLogger(RecommendationJob.class);
    private static final int SLIDING_WINDOW_DAYS = 30;

    @Autowired
    private RecommendationConfig config;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AssociationRuleRepository associationRuleRepository;

    @Scheduled(initialDelay = 5000, fixedRateString = "${recommendation.refresh-rate-ms:3600000}")
    @Transactional
    public void recompute() {
        logger.info("[RecommendationJob] Bắt đầu chạy FP-Growth quét dữ liệu {} ngày...", SLIDING_WINDOW_DAYS);
        
        try {
            // Lấy danh sách (OrderId, BookId) của các đơn hàng thành công/đang xử lý
            LocalDateTime windowStart = LocalDateTime.now().minusDays(SLIDING_WINDOW_DAYS);
            List<Object[]> pairs = orderItemRepository.findOrderBookPairsByStatusesAndDateRange(
                Arrays.asList(OrderStatus.PROCESSING, OrderStatus.COMFIRMED, OrderStatus.SHIPPING, OrderStatus.COMPLETED),
                windowStart
            );

            if (pairs.isEmpty()) {
                logger.warn("[RecommendationJob] Không có đơn hàng nào để tính toán. Bỏ qua.");
                return;
            }

            // Gộp theo OrderId để tạo thành giỏ hàng (Basket)
            Map<Long, List<Long>> transactionsMap = new HashMap<>();
            for (Object[] row : pairs) {
                Long orderId = (Long) row[0];
                Long bookId = (Long) row[1];
                transactionsMap.computeIfAbsent(orderId, k -> new ArrayList<>()).add(bookId);
            }
            List<List<Long>> transactions = new ArrayList<>(transactionsMap.values());

            // Chạy thuật toán FP-Growth
            FPGrowthAlgorithm fpMiner = new FPGrowthAlgorithm(
                config.getMinSupport(), 
                config.getMinConfidence(), 
                config.getMinLift()
            );

            Map<Long, List<com.example.bookstore.service.recommendation.fpgrowth.AssociationRule>> rulesMap = fpMiner.mineRules(transactions);
            
            // Map kết quả sang Entity Database
            List<AssociationRule> dbRules = convertToDatabaseEntities(rulesMap);

            // Cập nhật DB Atomic
            int deletedCount = associationRuleRepository.deleteAllRules();
            if (!dbRules.isEmpty()) {
                associationRuleRepository.saveAll(dbRules);
                logger.info("[RecommendationJob] Thành công: Đã xóa {} luật cũ, chèn {} luật mới", deletedCount, dbRules.size());
            }

        } catch (Exception e) {
            logger.error("[RecommendationJob] Lỗi nghiêm trọng khi chạy FP-Growth", e);
        }
    }

    private List<AssociationRule> convertToDatabaseEntities(
            Map<Long, List<com.example.bookstore.service.recommendation.fpgrowth.AssociationRule>> rulesMap) {
        
        List<AssociationRule> dbRules = new ArrayList<>();
        int maxRecommendations = config.getMaxBoughtTogether();
        
        for (Map.Entry<Long, List<com.example.bookstore.service.recommendation.fpgrowth.AssociationRule>> entry : rulesMap.entrySet()) {
            Long bookIdA = entry.getKey();
            
            Optional<Book> bookAOpt = bookRepository.findById(bookIdA);
            if (bookAOpt.isEmpty()) continue;
            Book bookA = bookAOpt.get();
            
            // Lấy Top N luật tốt nhất cho Sách A
            entry.getValue().stream().limit(maxRecommendations).forEach(rule -> {
                Optional<Book> bookBOpt = bookRepository.findById(rule.getConsequent());
                if (bookBOpt.isEmpty()) return;
                
                AssociationRule dbRule = new AssociationRule();
                dbRule.setBookA(bookA);
                dbRule.setBookB(bookBOpt.get());
                
                // Set chính xác support, confidence, lift từ thuật toán
                dbRule.setSupport(BigDecimal.valueOf(rule.getSupport()).setScale(4, java.math.RoundingMode.HALF_UP));
                dbRule.setConfidence(BigDecimal.valueOf(rule.getConfidence()).setScale(4, java.math.RoundingMode.HALF_UP));
                dbRule.setLift(BigDecimal.valueOf(rule.getLift()).setScale(4, java.math.RoundingMode.HALF_UP));
                dbRule.setUpdatedAt(LocalDateTime.now());
                
                dbRules.add(dbRule);
            });
        }
        return dbRules;
    }
}