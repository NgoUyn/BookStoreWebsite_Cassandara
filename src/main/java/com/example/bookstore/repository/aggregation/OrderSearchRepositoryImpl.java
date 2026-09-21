package com.example.bookstore.repository.aggregation;

import com.example.bookstore.model.Order;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.count;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.replaceRoot;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

/**
 * Hien thuc truy vấn nang cao cho orders bang Aggregation pipeline:
 * {@code $match -> $unwind subOrders -> $match -> $replaceRoot}
 * (tuong duong JOIN FETCH cua ban SQL nhung chi 1 collection).
 */
@Repository
@RequiredArgsConstructor
public class OrderSearchRepositoryImpl implements OrderSearchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Order> findBuyerOrdersWithFilters(Long buyerId,
                                                 LocalDateTime createdFrom,
                                                 LocalDateTime createdTo,
                                                 Double minAmount,
                                                 Double maxAmount,
                                                 Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("buyerId").is(buyerId));
        if (createdFrom != null) {
            and.add(Criteria.where("createdAt").gte(createdFrom));
        }
        if (createdTo != null) {
            and.add(Criteria.where("createdAt").lte(createdTo));
        }
        if (minAmount != null) {
            and.add(Criteria.where("totalAmount").gte(minAmount));
        }
        if (maxAmount != null) {
            and.add(Criteria.where("totalAmount").lte(maxAmount));
        }
        Criteria criteria = new Criteria().andOperator(and.toArray(new Criteria[0]));
        long total = mongoTemplate.count(Query.query(criteria), Order.class);
        List<Order> content = mongoTemplate.find(
                Query.query(criteria).with(pageable).with(Sort.by(Sort.Direction.DESC, "createdAt")), Order.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<SubOrder> findSubOrdersBySellerWithFilters(Long sellerId,
                                                           OrderStatus status,
                                                           LocalDateTime createdFrom,
                                                           LocalDateTime createdTo,
                                                           Double minAmount,
                                                           Double maxAmount,
                                                           Pageable pageable) {
        List<Criteria> subMatch = new ArrayList<>();
        subMatch.add(Criteria.where("subOrders.sellerId").is(sellerId));
        if (status != null) {
            subMatch.add(Criteria.where("subOrders.status").is(status));
        }
        if (createdFrom != null) {
            subMatch.add(Criteria.where("createdAt").gte(createdFrom));
        }
        if (createdTo != null) {
            subMatch.add(Criteria.where("createdAt").lte(createdTo));
        }
        if (minAmount != null) {
            subMatch.add(Criteria.where("subOrders.subTotal").gte(minAmount));
        }
        if (maxAmount != null) {
            subMatch.add(Criteria.where("subOrders.subTotal").lte(maxAmount));
        }
        Criteria criteria = new Criteria().andOperator(subMatch.toArray(new Criteria[0]));

        // Dem: $unwind roi dem theo subOrder
        long total = subOrderCount(criteria);

        Aggregation aggregation = Aggregation.newAggregation(
                match(criteria),
                unwind("$subOrders"),
                match(Criteria.where("subOrders.sellerId").is(sellerId)),
                replaceRoot("$subOrders"),
                sort(Sort.Direction.DESC, "subOrderId"),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize())
        );
        AggregationResults<SubOrder> results = mongoTemplate.aggregate(aggregation, "orders", SubOrder.class);
        return new PageImpl<>(results.getMappedResults(), pageable, total);
    }

    private long subOrderCount(Criteria criteria) {
        Aggregation countAgg = Aggregation.newAggregation(
                match(criteria),
                unwind("$subOrders"),
                count().as("total")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(countAgg, "orders", Map.class);
        List<Map> mapped = results.getMappedResults();
        if (mapped.isEmpty()) {
            return 0L;
        }
        Object value = mapped.get(0).get("total");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @Override
    public List<SubOrder> findSubOrdersBySeller(Long sellerId, OrderStatus status, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("subOrders.sellerId").is(sellerId));
        if (status != null) {
            and.add(Criteria.where("subOrders.status").is(status));
        }
        Criteria criteria = new Criteria().andOperator(and.toArray(new Criteria[0]));
        Aggregation aggregation = Aggregation.newAggregation(
                match(criteria),
                unwind("$subOrders"),
                match(buildSubOrderMatch(sellerId, status)),
                replaceRoot("$subOrders"),
                sort(Sort.Direction.DESC, "subOrderId"),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize())
        );
        return mongoTemplate.aggregate(aggregation, "orders", SubOrder.class).getMappedResults();
    }

    private Criteria buildSubOrderMatch(Long sellerId, OrderStatus status) {
        Criteria criteria = Criteria.where("subOrders.sellerId").is(sellerId);
        if (status != null) {
            criteria = criteria.and("subOrders.status").is(status);
        }
        return criteria;
    }

    @Override
    public List<SubOrder> searchSubOrdersByBuyerName(Long sellerId, String buyerName, Pageable pageable) {
        String regex = escapeRegex(buyerName);
        Criteria nameCriteria = new Criteria().andOperator(
                Criteria.where("subOrders.sellerId").is(sellerId),
                new Criteria().orOperator(
                        Criteria.where("subOrders.buyer.username").regex(regex, "i"),
                        Criteria.where("subOrders.buyer.fullName").regex(regex, "i")));
        Aggregation aggregation = Aggregation.newAggregation(
                match(nameCriteria),
                unwind("$subOrders"),
                match(nameCriteria),
                replaceRoot("$subOrders"),
                sort(Sort.Direction.DESC, "subOrderId"),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize())
        );
        return mongoTemplate.aggregate(aggregation, "orders", SubOrder.class).getMappedResults();
    }

    @Override
    public List<Map> aggregateTopSellingBooks(LocalDateTime fromDate, int limit) {
        Document matchDoc = new Document("isDeleted", new Document("$ne", true));
        if (fromDate != null) {
            matchDoc.append("createdAt", new Document("$gte", fromDate));
        }
        List<Document> pipeline = List.of(
                new Document("$match", matchDoc),
                new Document("$unwind", "$subOrders"),
                new Document("$match", new Document("subOrders.status",
                        new Document("$in", List.of("COMPLETED", "DELIVERED")))),
                new Document("$unwind", "$subOrders.items"),
                new Document("$group", new Document("_id", "$subOrders.items.bookId")
                        .append("sold", new Document("$sum", "$subOrders.items.quantity"))
                        .append("title", new Document("$first", "$subOrders.items.title"))
                        .append("revenue", new Document("$sum", new Document("$multiply",
                                List.of("$subOrders.items.quantity", "$subOrders.items.unitPrice"))))),
                new Document("$sort", new Document("sold", -1)),
                new Document("$limit", limit)
        );
        return runPipeline(pipeline, "orders");
    }

    /**
     * Dashboard cua seller trong 1 REQUEST = 1 pipeline ({@code $facet}):
     * overview + top sach + phan bo trang thai + doanh thu theo ngay.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> sellerDashboardFacet(Long sellerId, LocalDateTime fromDate) {
        Document matchDoc = new Document("subOrders.sellerId", sellerId);
        if (fromDate != null) {
            matchDoc.append("createdAt", new Document("$gte", fromDate));
        }
        Document facetStage = new Document("$facet", new Document()
                .append("overview", List.of(
                        new Document("$group", new Document("_id", null)
                                .append("revenue", new Document("$sum", "$subOrders.subTotal"))
                                .append("orderCount", new Document("$sum", 1))
                                .append("avgOrder", new Document("$avg", "$subOrders.subTotal")))))
                .append("topBooks", List.of(
                        new Document("$unwind", "$subOrders.items"),
                        new Document("$group", new Document("_id", "$subOrders.items.bookId")
                                .append("title", new Document("$first", "$subOrders.items.title"))
                                .append("sold", new Document("$sum", "$subOrders.items.quantity"))),
                        new Document("$sort", new Document("sold", -1)),
                        new Document("$limit", 5)))
                .append("statusBreakdown", List.of(
                        new Document("$group", new Document("_id", "$subOrders.status")
                                .append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("count", -1))))
                .append("revenueByDay", List.of(
                        new Document("$group", new Document("_id", new Document("$dateToString",
                                        new Document("format", "%Y-%m-%d").append("date", "$createdAt")))
                                .append("revenue", new Document("$sum", "$subOrders.subTotal"))
                                .append("orderCount", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1)))));

        List<Document> pipeline = List.of(
                new Document("$match", matchDoc),
                new Document("$unwind", "$subOrders"),
                new Document("$match", new Document("subOrders.sellerId", sellerId)),
                facetStage
        );
        List<Map> rows = runPipeline(pipeline, "orders");
        if (rows.isEmpty()) {
            return Map.of();
        }
        Object dashboard = rows.get(0);
        if (dashboard instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /** Chay pipeline JSON tho (linh hoat cho cac stage nang cao nhu $facet). */
    @SuppressWarnings("rawtypes")
    private List<Map> runPipeline(List<Document> pipeline, String collection) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> operations = new ArrayList<>();
        for (Document stage : pipeline) {
            operations.add(context -> stage);
        }
        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, collection, Map.class).getMappedResults();
    }

    /** Chuyen tu khoa nguoi dung thanh regex an toan. */
    private String escapeRegex(String input) {
        return input == null ? "" : input.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1");
    }
}


