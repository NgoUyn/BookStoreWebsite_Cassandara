package com.example.bookstore.repository;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>,JpaSpecificationExecutor<Book>{
    // Ví dụ: Tìm sách theo tiêu đề để hỗ trợ gợi ý sản phẩm
    List<Book> findByTitleContaining(String title);


    // 1. Dùng cho Trang chủ (index.html) - CHỈ hiển thị sách đã duyệt và đang bán
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"category", "seller"})
    Page<Book> findByApprovalStatusAndIsActiveTrue(ApprovalStatus status, Pageable pageable);
    // 2. Dùng cho mục "Thường được mua kèm" (Details_Produce.html)
    // Lấy sách cùng danh mục, trừ cuốn hiện tại ra, ưu tiên sách đã duyệt
    List<Book> findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
            Long categoryId,
            Long currentBookId,
            ApprovalStatus status,
            Pageable pageable
    );

    List<Book> findBySeller(User seller);

    long countBySellerId(Long sellerId);

    @Query("SELECT b.approvalStatus FROM Book b WHERE b.id = :bookId")
    ApprovalStatus findApprovalStatusById(@Param("bookId") Long bookId);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END
            FROM Book b
            WHERE b.id = :bookId
              AND b.seller.id = :sellerId
            """)
    boolean existsByIdAndSellerId(@Param("bookId") Long bookId, @Param("sellerId") Long sellerId);

    // S02: Lấy sách theo trạng thái (Có phân trang cho Admin)
    Page<Book> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    List<Book> findByApprovalStatus(ApprovalStatus approvalStatus);

    // 🆕 NEW: Các phương thức hỗ trợ Admin quản lý
    List<Book> findByIsActive(boolean isActive);

    Page<Book> findByIsActive(boolean isActive, Pageable pageable);

    Page<Book> findByApprovalStatusAndIsActive(
            ApprovalStatus status,
            boolean isActive,
            Pageable pageable
    );

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);



    @Query("""
            SELECT b FROM Book b
            WHERE b.approvalStatus = :status
              AND (
                :q IS NULL
                OR b.title LIKE CONCAT('%', :q, '%')
                OR b.author LIKE CONCAT('%', :q, '%')
              )
            ORDER BY
              CASE
                WHEN :q IS NOT NULL AND b.title LIKE CONCAT(:q, '%') THEN 0
                WHEN :q IS NOT NULL AND b.author LIKE CONCAT(:q, '%') THEN 1
                ELSE 2
              END,
              b.title ASC
            """)
    List<Book> findSuggestions(@Param("q") String q,
                               @Param("status") ApprovalStatus status,
                               Pageable pageable);

    @Query("""
            SELECT b FROM Book b
            JOIN OrderItem oi ON oi.book = b
            JOIN oi.subOrder so
            JOIN so.parentOrder o
            WHERE b.approvalStatus = :status
            GROUP BY b
            ORDER BY SUM(oi.quantity) DESC, MAX(o.createdAt) DESC, b.id DESC
            """)
    List<Book> findBestSellingBooks(@Param("status") ApprovalStatus status, Pageable pageable);

    @Query("""
            SELECT b FROM Book b
            JOIN OrderItem oi ON oi.book = b
            JOIN oi.subOrder so
            JOIN so.parentOrder o
            WHERE b.approvalStatus = :status
              AND o.createdAt >= :since
            GROUP BY b
            ORDER BY SUM(oi.quantity) DESC, MAX(o.createdAt) DESC, b.id DESC
            """)
    List<Book> findTrendingBooks(@Param("status") ApprovalStatus status,
                                 @Param("since") java.time.LocalDateTime since,
                                 Pageable pageable);

    /**
     * Tìm sách của seller (dùng cho Inventory Management - S03)
     * Bao gồm tất cả trạng thái: PENDING, APPROVED, REJECTED
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT b FROM Book b
            LEFT JOIN b.category c
            WHERE b.seller.id = :sellerId
              AND (
                :q IS NULL
                OR :q = ''
                OR b.title LIKE CONCAT('%', :q, '%')
                OR b.author LIKE CONCAT('%', :q, '%')
              )
              AND (
                :categoryId IS NULL
                OR c.id = :categoryId
              )
            """)
    Page<Book> findBySellerIdAndKeywordAndCategory(
            @Param("sellerId") Long sellerId,
            @Param("q") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable);


    @Query(value = """
        SELECT b.* FROM books b
        LEFT JOIN category c ON b.category_id = c.id
        WHERE b.id IN (
            -- Luồng chạy siêu tốc: Tìm ID dựa trên Index RAM, gỡ bỏ hàm LOWER gây chậm
            SELECT id FROM books 
            WHERE approval_status = :status 
              AND is_active = 1
              AND (:q IS NULL OR :q = '' OR title LIKE CONCAT('%', :q, '%') OR author LIKE CONCAT('%', :q, '%') OR publisher LIKE CONCAT('%', :q, '%'))
        )
        AND (:categoryIds IS NULL OR c.id IN (:categoryIds))
        AND (:sellerIds IS NULL OR b.seller_id IN (:sellerIds))
        AND (:publishers IS NULL OR b.publisher IN (:publishers))
        AND (:author IS NULL OR b.author LIKE CONCAT('%', :author, '%'))
        AND (:minPrice IS NULL OR b.price >= :minPrice)
        AND (:maxPrice IS NULL OR b.price <= :maxPrice)
        AND (:minRating IS NULL OR b.average_rating >= :minRating)
        AND (:inStock IS NULL OR :inStock = 0 OR b.stock_quantity > 0)
        AND (:publishYearFrom IS NULL OR b.publish_year >= :publishYearFrom)
        AND (:publishYearTo IS NULL OR b.publish_year <= :publishYearTo)
        """,
            countQuery = """
        SELECT COUNT(*) FROM books b
        LEFT JOIN category c ON b.category_id = c.id
        WHERE b.approval_status = :status
          AND b.is_active = 1
          AND (:q IS NULL OR :q = '' OR b.title LIKE CONCAT('%', :q, '%') OR b.author LIKE CONCAT('%', :q, '%') OR b.publisher LIKE CONCAT('%', :q, '%'))
          AND (:categoryIds IS NULL OR c.id IN (:categoryIds))
          AND (:sellerIds IS NULL OR b.seller_id IN (:sellerIds))
          AND (:publishers IS NULL OR b.publisher IN (:publishers))
          AND (:author IS NULL OR b.author LIKE CONCAT('%', :author, '%'))
          AND (:minPrice IS NULL OR b.price >= :minPrice)
          AND (:maxPrice IS NULL OR b.price <= :maxPrice)
          AND (:minRating IS NULL OR b.average_rating >= :minRating)
          AND (:inStock IS NULL OR :inStock = 0 OR b.stock_quantity > 0)
          AND (:publishYearFrom IS NULL OR b.publish_year >= :publishYearFrom)
          AND (:publishYearTo IS NULL OR b.publish_year <= :publishYearTo)
        """,
            nativeQuery = true)
    Page<Book> searchApprovedBooksNative(
            @Param("q") String q,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("sellerIds") List<Long> sellerIds,
            @Param("publishers") List<String> publishers,
            @Param("author") String author,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("minRating") Double minRating,
            @Param("inStock") Boolean inStock,
            @Param("publishYearFrom") Integer publishYearFrom,
            @Param("publishYearTo") Integer publishYearTo,
            @Param("status") String status,
            Pageable pageable
    );

    /**
     * Lấy danh sách danh mục kèm số lượng sách đã APPROVED của một seller.
     * Query tối ưu: chỉ scan trên index (seller_id, approval_status, is_active, category_id)
     */
    @Query(value = """
        SELECT c.id, c.name, COUNT(b.id) AS cnt
        FROM category c
        INNER JOIN books b ON b.category_id = c.id
        WHERE b.seller_id = :sellerId
          AND b.approval_status = :status
          AND b.is_active = 1
        GROUP BY c.id, c.name
        ORDER BY c.name ASC
        """, nativeQuery = true)
    List<Object[]> countBooksByCategoryAndSeller(
            @Param("sellerId") Long sellerId,
            @Param("status") String status
    );

}


