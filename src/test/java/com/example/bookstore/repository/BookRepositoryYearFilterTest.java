package com.example.bookstore.repository;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.embedded.BookImage;
import com.example.bookstore.model.embedded.BookStats;
import com.example.bookstore.model.embedded.RatingSummary;
import com.example.bookstore.model.enums.ApprovalStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test truy vấn trên MongoDB (thay ban @DataJpaTest + H2 cua SQL Server).
 *
 * <p>Kiem chung cac dieu kien loc cua trang tim kiem:
 * khoang gia {@code finalPrice}, khoang nam {@code publishYear},
 * trang thai duyet va sap xep theo counter {@code stats.soldCount}.</p>
 *
 * <p>Dung instance MongoDB cua do an (cong 27018 - replica set rs0) voi
 * database rieng {@code bookom_test} de KHONG anh huong du lieu that.</p>
 */
@DataMongoTest
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27018/bookom_test?replicaSet=rs0",
        "spring.data.mongodb.database=bookom_test",
        "spring.data.mongodb.auto-index-creation=false",
        "app.mongo.index-verify-on-startup=false"
})
class BookRepositoryYearFilterTest {

    private static final String COLLECTION = "books";

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), COLLECTION);
        mongoTemplate.insert(buildBook(1L, "Old", 1999, 120000.0, 5, 10));
        mongoTemplate.insert(buildBook(2L, "Middle", 2005, 150000.0, 50, 3));
        mongoTemplate.insert(buildBook(3L, "New", 2020, 350000.0, 20, 1));
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.remove(new Query(), COLLECTION);
    }

    @Test
    void filterByPublishYearRange_shouldReturnOnlyMatchingBooks() {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("approvalStatus").is(ApprovalStatus.APPROVED),
                Criteria.where("isActive").is(true),
                Criteria.where("publishYear").gte(2000).lte(2010));

        List<Book> result = mongoTemplate.find(Query.query(criteria), Book.class, COLLECTION);

        assertThat(result).extracting(Book::getTitle).containsExactly("Middle");
    }

    @Test
    void filterByPriceRangeAndStatus_shouldExcludeOtherBooks() {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("approvalStatus").is(ApprovalStatus.APPROVED),
                Criteria.where("isActive").is(true),
                Criteria.where("finalPrice").gte(100000.0).lte(200000.0));

        List<Book> result = mongoTemplate.find(Query.query(criteria), Book.class, COLLECTION);

        assertThat(result).extracting(Book::getTitle).containsExactlyInAnyOrder("Old", "Middle");
    }

    @Test
    void sortBySoldCount_shouldReturnBestSellerFirst() {
        Query query = Query.query(Criteria.where("approvalStatus").is(ApprovalStatus.APPROVED))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "stats.soldCount"));

        List<Book> result = mongoTemplate.find(query, Book.class, COLLECTION);

        assertThat(result).extracting(Book::getTitle).containsExactly("Middle", "New", "Old");
    }

    @Test
    void hiddenFieldsOfReadModel_shouldBePersisted() {
        Book loaded = mongoTemplate.findById(3L, Book.class, COLLECTION);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getRating().getAvg()).isEqualTo(4.5);
        assertThat(loaded.getStats().getSoldCount()).isEqualTo(20);
        assertThat(loaded.getImageUrl()).isEqualTo("/covers/3_s.jpg");   // getter tuong thich frontend
        assertThat(loaded.getAverageRating()).isEqualTo(4.5);
    }

    private Book buildBook(Long id, String title, Integer publishYear, Double price,
                           Integer soldCount, Integer stock) {
        return Book.builder()
                .id(id)
                .title(title)
                .author("Author " + id)
                .description("desc")
                .price(price)
                .finalPrice(price)
                .stockQuantity(stock)
                .publisher("NXB Test")
                .publishYear(publishYear)
                .images(BookImage.builder().thumbnail("/covers/" + id + "_s.jpg").build())
                .approvalStatus(ApprovalStatus.APPROVED)
                .isActive(true)
                .rating(RatingSummary.builder().avg(4.5).count(2).build())
                .stats(BookStats.builder().soldCount(soldCount).viewCount(0).wishlistCount(0).build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
