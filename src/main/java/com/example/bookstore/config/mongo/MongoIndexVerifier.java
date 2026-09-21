package com.example.bookstore.config.mongo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kiem tra (khong tao) cac index QUAN TRONG co ton tai hay khong luc khoi dong.
 *
 * <p>Index duoc quan ly bang code: {@code db/mongo/02_indexes.js}.
 * Component nay chi CANH BAO neu thieu, giup phat hien som khi ai do
 * restore DB cu hoac chay tren may chua chay script index.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mongo.index-verify-on-startup", havingValue = "true", matchIfMissing = true)
public class MongoIndexVerifier implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    /** collection -> danh sach index bat buoc phai co */
    private static Map<String, List<String>> requiredIndexes() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("users", List.of("uq_users_username", "idx_users_role_active"));
        map.put("books", List.of("idx_books_catalog", "idx_books_price_rating", "txt_books"));
        map.put("carts", List.of("uq_carts_buyer", "idx_carts_items_book"));
        map.put("orders", List.of("uq_orders_code", "idx_orders_buyer_created", "idx_orders_seller_status"));
        map.put("reviews", List.of("uq_reviews_book_user", "idx_reviews_book_visible"));
        map.put("seller_shops", List.of("uq_shops_slug", "geo_shops_location"));
        map.put("notifications", List.of("idx_notifications_user", "ttl_notifications_expires"));
        map.put("notification_deliveries", List.of("idx_nd_pending_retry"));
        map.put("coupons", List.of("uq_coupons_code_ci"));
        map.put("counters", List.of()); // chi co _id_
        return map;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        try {
            requiredIndexes().forEach((collection, indexes) -> {
                List<String> existing = mongoTemplate.indexOps(collection).getIndexInfo().stream()
                        .map(info -> info.getName() == null ? "" : info.getName())
                        .toList();
                for (String index : indexes) {
                    if (!existing.contains(index)) {
                        missing.add(collection + "." + index);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("[MongoIndexVerifier] Khong kiem tra duoc index (MongoDB chua san sang?): {}",
                    e.getMessage());
            return;
        }
        if (missing.isEmpty()) {
            log.info("[MongoIndexVerifier] OK: tat ca index bat buoc deu ton tai.");
        } else {
            log.warn("[MongoIndexVerifier] THIEU {} index: {}. Hay chay: tools\\mongo-run-scripts.bat",
                    missing.size(), missing);
        }
    }
}
