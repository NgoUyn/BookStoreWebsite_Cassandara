package com.example.bookstore.repository.aggregation;

import com.example.bookstore.dto.CategoryWithCount;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Truy vấn nang cao cho books (thay cac native SQL/JPQL phuc tap cua ban cu).
 *
 * <p>Duoc hien thuc trong {@link BookSearchRepositoryImpl} bang MongoTemplate +
 * {@code Criteria}/{@code Aggregation} (khong dung JPQL vi MongoDB khong co).</p>
 */
public interface BookSearchRepository {

    /**
     * Tim sach da duyet voi 11 tieu chi loc (tuong duong
     * {@code searchApprovedBooksNative} cua ban SQL Server).
     */
    Page<Book> searchApprovedBooks(String q,
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
                                   Pageable pageable);

    /**
     * Goi y autocomplete: uu tien title bat dau bang tu khoa, sau do author,
     * cuoi cung la chua tu khoa (tuong duong findSuggestions cua ban cu).
     */
    List<Book> findSuggestions(String q, ApprovalStatus status, Pageable pageable);

    /** Top sach ban chay theo counter {@code stats.soldCount} (khong GROUP BY). */
    List<Book> findBestSellingBooks(ApprovalStatus status, Pageable pageable);

    /** Danh muc + so luong sach da duyet cua 1 seller (aggregation $group). */
    List<CategoryWithCount> countBooksByCategoryAndSeller(Long sellerId, ApprovalStatus status);

    /** Tim kiem cua seller voi tu khoa + danh muc (thay findBySellerIdAndKeywordAndCategory). */
    Page<Book> searchSellerBooks(Long sellerId, String keyword, Long categoryId, Pageable pageable);

    /**
     * Sach "dang hot": ban chay trong khoang thoi gian gan day.
     * Dung counter denormalized {@code stats.soldCount} + loc theo updatedAt.
     */
    List<Book> findTrendingBooks(ApprovalStatus status, java.time.LocalDateTime since, Pageable pageable);

    /**
     * Dem so sach theo tung danh muc (dashboard admin) - chay server-side
     * thay vi nap toan bo collection vao RAM (248k sach).
     */
    Map<String, Long> countBooksByCategoryName();
}
