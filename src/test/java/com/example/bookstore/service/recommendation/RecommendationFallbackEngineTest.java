package com.example.bookstore.service.recommendation;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationFallbackEngineTest {

    @Mock
    private BookRepository bookRepository;

    private RecommendationFallbackEngine fallbackEngine;

    @BeforeEach
    void setUp() {
        fallbackEngine = new RecommendationFallbackEngine();
        ReflectionTestUtils.setField(fallbackEngine, "bookRepository", bookRepository);
    }

    @Test
    void fallbackSameAuthorOrCategory_shouldNotThrowWhenSourceBookHasNoCategory() {
        Book sourceBook = buildBook(1L, "Source", "Author A", null);
        Book sameAuthor = buildBook(2L, "Same author", "Author A", null);
        Book otherBook = buildBook(3L, "Other", "Author B", null);

        when(bookRepository.findByApprovalStatus(ApprovalStatus.APPROVED))
            .thenReturn(List.of(sourceBook, sameAuthor, otherBook));

        List<Book> result = assertDoesNotThrow(() ->
            fallbackEngine.fallbackSameAuthorOrCategory(sourceBook, 5, List.of())
        );

        assertThat(result)
            .extracting(Book::getId)
            .containsExactly(2L, 3L);
    }

    private Book buildBook(Long id, String title, String author, Category category) {
        Book book = new Book();
        book.setId(id);
        book.setTitle(title);
        book.setAuthor(author);
        book.setDescription("desc");
        book.setPrice(100000.0);
        book.setStockQuantity(10);
        book.setImageUrl("/img.png");
        book.setPublisher("NXB");
        book.setPublishYear(2024);
        book.setCategory(category);
        book.setSeller(User.builder().id(10L).username("seller").passwordHash("x").role(UserRole.SELLER).build());
        book.setApprovalStatus(ApprovalStatus.APPROVED);
        return book;
    }
}
