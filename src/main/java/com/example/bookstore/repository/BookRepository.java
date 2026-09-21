package com.example.bookstore.repository;

import com.example.bookstore.dto.CategoryWithCount;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.aggregation.BookSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository AGGREGATE books.
 *
 * <p>Cac truy vấn phuc tap (tim kiem nhieu tieu chi, goi y, thong ke danh muc)
 * duoc tach sang {@link BookSearchRepository} dung MongoTemplate + Aggregation,
 * vi JPQL/native SQL cua ban cu khong con.</p>
 */
@Repository
public interface BookRepository extends MongoRepository<Book, Long>, BookSearchRepository {

    // ------------------------- Tra cuu co ban ------------------------------
    List<Book> findByTitleContaining(String title);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Book> findByIdIn(List<Long> ids);

    List<Book> findByCategoryId(Long categoryId);

    // ------------------------- Theo trang thai -----------------------------
    Page<Book> findByApprovalStatus(ApprovalStatus status, Pageable pageable);

    List<Book> findByApprovalStatus(ApprovalStatus status);

    Page<Book> findByApprovalStatusAndIsActive(ApprovalStatus status, boolean isActive, Pageable pageable);

    Page<Book> findByApprovalStatusAndIsActiveTrue(ApprovalStatus status, Pageable pageable);

    List<Book> findByIsActive(boolean isActive);

    Page<Book> findByIsActive(boolean isActive, Pageable pageable);

    // ------------------------- Theo nha ban --------------------------------
    List<Book> findBySellerId(Long sellerId);

    Page<Book> findBySellerId(Long sellerId, Pageable pageable);

    List<Book> findBySellerIdAndApprovalStatus(Long sellerId, ApprovalStatus status);

    long countBySellerId(Long sellerId);

    long countBySellerIdAndApprovalStatus(Long sellerId, ApprovalStatus status);

    @Query(value = "{ '_id': ?0, 'sellerId': ?1 }", exists = true)
    boolean existsByIdAndSellerId(Long bookId, Long sellerId);

    /** Chi lay trang thai duyet (projection) - nhe hon doc ca document. */
    @Query(value = "{ '_id': ?0 }", fields = "{ 'approvalStatus': 1 }")
    Optional<Book> findApprovalStatusById(Long bookId);

    // --------------------- Goi y / ban chay --------------------------------
    List<Book> findByApprovalStatusAndIsActiveTrueOrderByStatsSoldCountDesc(ApprovalStatus status, Pageable pageable);

    List<Book> findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
            Long categoryId, Long currentBookId, ApprovalStatus status, Pageable pageable);

    List<Book> findByAuthorAndIdNotAndApprovalStatusAndIsActiveTrue(
            String author, Long currentBookId, ApprovalStatus status, Pageable pageable);

    /** Sach cung danh muc bang multikey index {@code idx_books_catalog}. */
    @Query("{ 'categoryId': ?0, '_id': { $ne: ?1 }, 'approvalStatus': 'APPROVED', 'isActive': true }")
    List<Book> findSimilarBooks(Long categoryId, Long currentBookId, Pageable pageable);

    /** Tim sach theo tag - multikey index {@code idx_books_tags}. */
    @Query("{ 'tags': ?0, 'approvalStatus': 'APPROVED', 'isActive': true }")
    List<Book> findByTag(String tag, Pageable pageable);

    // ======================================================================
    // LOP TUONG THICH (adapter) cho code cu truyen entity thay vi id
    // ======================================================================

    default List<Book> findBySeller(com.example.bookstore.model.User seller) {
        return seller == null || seller.getId() == null ? List.of() : findBySellerId(seller.getId());
    }

    default List<Book> findBySeller(com.example.bookstore.model.embedded.UserSnapshot seller) {
        return seller == null || seller.getId() == null ? List.of() : findBySellerId(seller.getId());
    }

    default long countBySeller(com.example.bookstore.model.User seller) {
        return seller == null || seller.getId() == null ? 0L : countBySellerId(seller.getId());
    }

    /**
     * TUONG THICH: code cu goi {@code countBooksByCategoryAndSeller(sellerId, status.name())}
     * va doc ket qua theo kieu {@code Object[]} (id, name, count) nhu native SQL.
     */
    default List<Object[]> countBooksByCategoryAndSeller(Long sellerId, String statusName) {
        List<CategoryWithCount> rows = countBooksByCategoryAndSeller(sellerId,
                ApprovalStatus.valueOf(statusName));
        List<Object[]> out = new ArrayList<>();
        for (CategoryWithCount row : rows) {
            out.add(new Object[]{row.getId(), row.getName(), row.getCount()});
        }
        return out;
    }
}
