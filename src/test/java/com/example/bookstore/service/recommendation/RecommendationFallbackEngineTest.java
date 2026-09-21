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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test goi y "cung tac gia / cung danh muc".
 *
 * <p>Da cap nhat theo ban sua hieu nang: engine KHONG con nap toan bo sach
 * ({@code findByApprovalStatus(APPROVED)}) ma day viec loc xuong DB voi 2 query
 * co index + gioi han ket qua.</p>
 */
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
    void fallback_shouldReturnSameAuthorFirstThenSameCategory() {
        Book source = buildBook(1L, "Source", "Author A", null);
        source.setCategoryId(5L);
        Book sameAuthor = buildBook(2L, "Same author", "Author A", null);
        Book sameCategory = buildBook(3L, "Same category", "Author B", null);
        sameCategory.setCategoryId(5L);

        when(bookRepository.findByAuthorAndApprovalStatusAndIsActiveTrueOrderByIdDesc(
                eq("Author A"), eq(ApprovalStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of(sameAuthor));
        when(bookRepository.findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
                eq(5L), eq(1L), eq(ApprovalStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of(sameCategory));

        List<Book> result = fallbackEngine.fallbackSameAuthorOrCategory(source, 5, List.of());

        assertThat(result).extracting(Book::getId).containsExactly(2L, 3L);
    }

    @Test
    void fallback_shouldNotQueryDbWhenBookHasNoAuthorAndNoCategory() {
        Book source = buildBook(1L, "Source", null, null);

        List<Book> result = fallbackEngine.fallbackSameAuthorOrCategory(source, 5, List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(bookRepository);
    }

    @Test
    void fallback_shouldSkipExcludedAndDuplicatedBooks() {
        Book source = buildBook(1L, "Source", "Author A", null);
        source.setCategoryId(5L);
        Book sameAuthor = buildBook(2L, "Same author", "Author A", null);
        Book excluded = buildBook(3L, "Excluded", "Author B", null);
        excluded.setCategoryId(5L);

        when(bookRepository.findByAuthorAndApprovalStatusAndIsActiveTrueOrderByIdDesc(
                eq("Author A"), eq(ApprovalStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of(sameAuthor, excluded));
        when(bookRepository.findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
                eq(5L), eq(1L), eq(ApprovalStatus.APPROVED), any(Pageable.class)))
                .thenReturn(List.of(excluded));

        List<Book> result = fallbackEngine.fallbackSameAuthorOrCategory(source, 5, List.of(excluded));

        assertThat(result).extracting(Book::getId).containsExactly(2L);
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
