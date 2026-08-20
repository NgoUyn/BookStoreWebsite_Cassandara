package com.example.bookstore.repository;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class BookRepositoryYearFilterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.example.bookstore.service.BookService bookService; // Tiêm BookService vào để test

    @Autowired
    private BookRepository bookRepository; // Giữ lại nếu các đoạn test khác dưới vẫn cần dùng

    @Test
    void searchApprovedBooks_shouldFilterPublishYearNumerically() {
        Category category = entityManager.persistAndFlush(new Category(null, "Fiction", null, null));

        User seller = User.builder()
            .username("seller")
            .passwordHash("secret")
            .role(UserRole.SELLER)
            .build();
        seller = entityManager.persistAndFlush(seller);

        entityManager.persistAndFlush(buildBook("Old", 1999, category, seller));
        entityManager.persistAndFlush(buildBook("Middle", 2005, category, seller));
        entityManager.persistAndFlush(buildBook("New", 2020, category, seller));

        Page<Book> result = bookService.searchApprovedBooks(
            null,
            null, // categoryIds
            null, // sellerId
                 null, //
            null, // author
            null, // minPrice
            null, // maxPrice
            null, // minRating
            null, // inStock
            2000, // publishYearFrom
            2010, // publishYearTo
            ApprovalStatus.APPROVED,
            PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
            .extracting(Book::getTitle)
            .containsExactly("Middle");
    }

    private Book buildBook(String title, Integer publishYear, Category category, User seller) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor("Author A");
        book.setDescription("desc");
        book.setPrice(100000.0);
        book.setStockQuantity(10);
        book.setImageUrl("/img.png");
        book.setPublisher("NXB");
        book.setPublishYear(publishYear);
        book.setCategory(category);
        book.setSeller(seller);
        book.setApprovalStatus(ApprovalStatus.APPROVED);
        return book;
    }
}
