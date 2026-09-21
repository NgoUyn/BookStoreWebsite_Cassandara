package com.example.bookstore.repository;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FACADE truy vấn {@code OrderItem} tren AGGREGATE {@code orders}.
 *
 * <p>SQL Server: {@code order_items} la bang rieng, phai JOIN 3 bang de lay
 * cap (orderId, bookId) phuc vu khai thac luat ket hop (FP-Growth).
 * MongoDB: chi can {@code $unwind} 2 cap tren document orders.</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderItemRepository {

    private final MongoTemplate mongoTemplate;

    /** Tat ca cap (orderId, bookId) - nguon du lieu cho thuat toan mua kem. */
    public List<Object[]> findAllOrderBookPairs() {
        return pairAggregation(null, null);
    }

    public List<Object[]> findAllOrderBookPairsByStatuses(List<OrderStatus> statuses) {
        return pairAggregation(statuses, null);
    }

    /** Sliing window: chi lay du lieu trong N ngay gan nhat (chong OOM). */
    public List<Object[]> findOrderBookPairsByStatusesAndDateRange(List<OrderStatus> statuses,
                                                                   LocalDateTime fromDate) {
        return pairAggregation(statuses, fromDate);
    }

    /** Kiem tra user da mua va don da hoan thanh (dung cho danh gia "da mua"). */
    public boolean hasUserPurchasedBook(User user, Long bookId) {
        if (user == null || user.getId() == null || bookId == null) {
            return false;
        }
        Document match = new Document("buyerId", user.getId())
                .append("isDeleted", new Document("$ne", true))
                .append("subOrders",
                        new Document("$elemMatch",
                                new Document("status", new Document("$in", List.of("COMPLETED")))
                                        .append("items.bookId", bookId)));
        Document count = new Document("$count", "total");
        List<Document> pipeline = List.of(new Document("$match", match), count);
        List<Map> rows = runPipeline(pipeline);
        if (rows.isEmpty()) {
            return false;
        }
        Object total = rows.get(0).get("total");
        return total instanceof Number number && number.longValue() > 0;
    }

    @SuppressWarnings("rawtypes")
    private List<Object[]> pairAggregation(List<OrderStatus> statuses, LocalDateTime fromDate) {
        Document match = new Document("isDeleted", new Document("$ne", true));
        if (fromDate != null) {
            match.append("createdAt", new Document("$gte", fromDate));
        }
        Document subMatch = new Document();
        if (statuses != null && !statuses.isEmpty()) {
            subMatch.append("$in", statuses.stream().map(Enum::name).toList());
        }

        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match));
        pipeline.add(new Document("$unwind", "$subOrders"));
        if (!subMatch.isEmpty()) {
            pipeline.add(new Document("$match", new Document("subOrders.status", subMatch)));
        }
        pipeline.add(new Document("$unwind", "$subOrders.items"));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("orderId", "$_id")
                .append("bookId", "$subOrders.items.bookId")));

        List<Map> rows = runPipeline(pipeline);
        List<Object[]> out = new ArrayList<>();
        for (Map row : rows) {
            out.add(new Object[]{row.get("orderId"), row.get("bookId")});
        }
        return out;
    }

    @SuppressWarnings("rawtypes")
    private List<Map> runPipeline(List<Document> pipeline) {
        List<AggregationOperation> operations = new ArrayList<>();
        for (Document stage : pipeline) {
            operations.add(context -> stage);
        }
        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "orders", Map.class).getMappedResults();
    }
}
