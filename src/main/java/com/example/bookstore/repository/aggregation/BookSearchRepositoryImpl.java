package com.example.bookstore.repository.aggregation;

import com.example.bookstore.dto.CategoryWithCount;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.enums.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

/**
 * Hien thuc cac truy vấn nang cao cho books bang MongoTemplate.
 *
 * <p>Thay the: JPQL {@code findSuggestions}, native SQL
 * {@code searchApprovedBooksNative} (11 tieu chi + JOIN category),
 * {@code findBestSellingBooks} (GROUP BY/SUM) va
 * {@code countBooksByCategoryAndSeller} (JOIN + GROUP BY).</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BookSearchRepositoryImpl implements BookSearchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Book> searchApprovedBooks(String q,
                                          List<Long> categoryIds,
                                          List<Long> sellerIds,
                                          List<String> publishers,
                                          String author,
                                          Double minPrice,
                                          Double maxPrice,
                                          Double minRating,
                                          Boolean inStock,
                                          Integer publishYearFrom,
                                          Integer publishYearTo,
                                          ApprovalStatus status,
                                          Pageable pageable) {
        Criteria criteria = buildSearchCriteria(q, categoryIds, sellerIds, publishers, author,
                minPrice, maxPrice, minRating, inStock, publishYearFrom, publishYearTo, status);
        long total = mongoTemplate.count(Query.query(criteria), Book.class);
        List<Book> content = mongoTemplate.find(Query.query(criteria).with(pageable), Book.class);
        return new PageImpl<>(content, pageable, total);
    }

    private Criteria buildSearchCriteria(String q, List<Long> categoryIds, List<Long> sellerIds,
                                         List<String> publishers, String author, Double minPrice,
                                         Double maxPrice, Double minRating, Boolean inStock,
                                         Integer publishYearFrom, Integer publishYearTo,
                                         ApprovalStatus status) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("approvalStatus").is(status));
        and.add(Criteria.where("isActive").is(true));

        if (q != null && !q.trim().isEmpty()) {
            String keyword = escapeRegex(q.trim());
            and.add(new Criteria().orOperator(
                    Criteria.where("title").regex(keyword, "i"),
                    Criteria.where("author").regex(keyword, "i"),
                    Criteria.where("publisher").regex(keyword, "i")));
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            and.add(Criteria.where("categoryId").in(categoryIds));
        }
        if (sellerIds != null && !sellerIds.isEmpty()) {
            and.add(Criteria.where("sellerId").in(sellerIds));
        }
        if (publishers != null && !publishers.isEmpty()) {
            and.add(Criteria.where("publisher").in(publishers));
        }
        if (author != null && !author.trim().isEmpty()) {
            and.add(Criteria.where("author").regex(escapeRegex(author.trim()), "i"));
        }
        if (minPrice != null) {
            and.add(Criteria.where("finalPrice").gte(minPrice));
        }
        if (maxPrice != null) {
            and.add(Criteria.where("finalPrice").lte(maxPrice));
        }
        if (minRating != null) {
            and.add(Criteria.where("rating.avg").gte(minRating));
        }
        if (inStock != null && inStock) {
            and.add(Criteria.where("stockQuantity").gt(0));
        }
        if (publishYearFrom != null) {
            and.add(Criteria.where("publishYear").gte(publishYearFrom));
        }
        if (publishYearTo != null) {
            and.add(Criteria.where("publishYear").lte(publishYearTo));
        }
        return new Criteria().andOperator(and.toArray(new Criteria[0]));
    }

    /**
     * Goi y autocomplete: chay 3 buoc uu tien (title bat dau -> author bat dau
     * -> chua tu khoa), gop lai va loai trung. Cach nay tan dung duoc index
     * (regex co ^) thay vi quet toan bo collection.
     */
    @Override
    public List<Book> findSuggestions(String q, ApprovalStatus status, Pageable pageable) {
        int limit = pageable.getPageSize();
        Map<Long, Book> merged = new LinkedHashMap<>();
        if (q != null && !q.trim().isEmpty()) {
            String raw = escapeRegex(q.trim());
            collect(merged, Criteria.where("title").regex("^" + raw, "i"), status, limit);
            collect(merged, Criteria.where("author").regex("^" + raw, "i"), status, limit);
            collect(merged, new Criteria().orOperator(
                    Criteria.where("title").regex(raw, "i"),
                    Criteria.where("author").regex(raw, "i")), status, limit);
        }
        return new ArrayList<>(merged.values());
    }

    private void collect(Map<Long, Book> merged, Criteria keywordCriteria, ApprovalStatus status, int limit) {
        if (merged.size() >= limit) {
            return;
        }
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("approvalStatus").is(status),
                Criteria.where("isActive").is(true),
                keywordCriteria);
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "title"))
                .limit(limit);
        for (Book book : mongoTemplate.find(query, Book.class)) {
            merged.putIfAbsent(book.getId(), book);
            if (merged.size() >= limit) {
                return;
            }
        }
    }

    @Override
    public List<Book> findBestSellingBooks(ApprovalStatus status, Pageable pageable) {
        // Da co counter denormalized stats.soldCount => chi sort + limit (KHONG GROUP BY)
        Query query = Query.query(Criteria.where("approvalStatus").is(status).and("isActive").is(true))
                .with(Sort.by(Sort.Direction.DESC, "stats.soldCount", "rating.avg"))
                .limit(pageable.getPageSize());
        if (pageable.getOffset() > 0) {
            query.skip(pageable.getOffset());
        }
        return mongoTemplate.find(query, Book.class);
    }

    @Override
    public List<CategoryWithCount> countBooksByCategoryAndSeller(Long sellerId, ApprovalStatus status) {
        Aggregation aggregation = Aggregation.newAggregation(
                match(Criteria.where("sellerId").is(sellerId).and("approvalStatus").is(status)),
                group("categoryId").count().as("count"),
                Aggregation.lookup("categories", "_id", "_id", "category"),
                Aggregation.unwind("category", true),
                sort(Sort.Direction.ASC, "category.name")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "books", Map.class);
        List<CategoryWithCount> out = new ArrayList<>();
        for (Map row : results.getMappedResults()) {
            String name = null;
            Object categoryDoc = row.get("category");
            if (categoryDoc instanceof Category category) {
                name = category.getName();
            } else if (categoryDoc instanceof Map<?, ?> map && map.get("name") != null) {
                name = String.valueOf(map.get("name"));
            }
            Object countValue = row.get("count");
            long count = countValue instanceof Number number ? number.longValue() : 0L;
            Object idValue = row.get("_id");
            Long id = idValue instanceof Number number ? number.longValue() : null;
            out.add(CategoryWithCount.builder().id(id).name(name).count(count).build());
        }
        return out;
    }

    @Override
    public Page<Book> searchSellerBooks(Long sellerId, String keyword, Long categoryId, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("sellerId").is(sellerId));
        if (categoryId != null) {
            and.add(Criteria.where("categoryId").is(categoryId));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = escapeRegex(keyword.trim());
            and.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("author").regex(regex, "i"),
                    Criteria.where("isbn").regex(regex, "i")));
        }
        Criteria criteria = new Criteria().andOperator(and.toArray(new Criteria[0]));
        long total = mongoTemplate.count(Query.query(criteria), Book.class);
        List<Book> content = mongoTemplate.find(Query.query(criteria).with(pageable), Book.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<Book> findTrendingBooks(ApprovalStatus status, java.time.LocalDateTime since, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("approvalStatus").is(status));
        and.add(Criteria.where("isActive").is(true));
        and.add(Criteria.where("stats.soldCount").gt(0));
        if (since != null) {
            and.add(Criteria.where("updatedAt").gte(since));
        }
        Query query = Query.query(new Criteria().andOperator(and.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "stats.soldCount", "rating.avg"))
                .limit(pageable.getPageSize());
        return mongoTemplate.find(query, Book.class);
    }

    /** Chuyen tu khoa nguoi dung thanh regex an toan (tranh ReDoS/injection). */
    private String escapeRegex(String input) {
        return input.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1");
    }
}

