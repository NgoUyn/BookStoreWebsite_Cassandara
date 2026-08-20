package com.example.bookstore.repository;

import com.example.bookstore.model.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Find coupon by code (case-insensitive)
     */
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Check if coupon code exists
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Find all active coupons
     */
    List<Coupon> findByIsActiveTrue();

    /**
     * Find all active and non-expired coupons
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE c.isActive = true 
        AND (c.expiresAt IS NULL OR c.expiresAt > CURRENT_TIMESTAMP)
        AND (c.totalQuantity < 0 OR c.usedCount < c.totalQuantity)
        ORDER BY c.createdAt DESC
    """)
    List<Coupon> findAllValidCoupons();

    /**
     * Find coupons with pagination
     */
    Page<Coupon> findByIsActive(boolean isActive, Pageable pageable);

    /**
     * Search coupons by code or description
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY c.createdAt DESC
    """)
    Page<Coupon> searchCoupons(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find expired coupons
     */
    List<Coupon> findByExpiresAtBefore(LocalDateTime now);

    /**
     * Find coupons that are about to expire (within 7 days)
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE c.expiresAt IS NOT NULL 
        AND c.expiresAt BETWEEN CURRENT_TIMESTAMP AND DATEADD(day, 7, CURRENT_TIMESTAMP)
        AND c.isActive = true
    """)
    List<Coupon> findExpiringCoupons();

    // ========== SELLER-SPECIFIC QUERIES ==========

    /**
     * Find all coupons owned by a specific seller (or global if seller_id is NULL)
     */
    Page<Coupon> findBySeller_IdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    /**
     * Find active coupons owned by a seller
     */
    Page<Coupon> findBySeller_IdAndIsActiveTrueOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    /**
     * Find coupon by code and seller (ensure seller-specific coupon)
     */
    Optional<Coupon> findByCodeIgnoreCaseAndSeller_Id(String code, Long sellerId);

    /**
     * Find coupon by code and seller, ensuring it's valid for use
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE LOWER(c.code) = LOWER(:code)
        AND c.seller.id = :sellerId
        AND c.isActive = true
        AND (c.startDate IS NULL OR c.startDate <= CURRENT_TIMESTAMP)
        AND (c.expiresAt IS NULL OR c.expiresAt > CURRENT_TIMESTAMP)
        AND (c.totalQuantity < 0 OR c.usedCount < c.totalQuantity)
    """)
    Optional<Coupon> findValidVoucherForSeller(@Param("code") String code, @Param("sellerId") Long sellerId);

    /**
     * Check if seller already has a coupon with this code
     */
    boolean existsByCodeIgnoreCaseAndSeller_Id(String code, Long sellerId);

    /**
     * Find all valid (active, not expired, not exhausted) coupons for a seller
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE c.seller.id = :sellerId
        AND c.isActive = true
        AND (c.startDate IS NULL OR c.startDate <= CURRENT_TIMESTAMP)
        AND (c.expiresAt IS NULL OR c.expiresAt > CURRENT_TIMESTAMP)
        AND (c.totalQuantity < 0 OR c.usedCount < c.totalQuantity)
        ORDER BY c.createdAt DESC
    """)
    List<Coupon> findAllValidVouchersForSeller(@Param("sellerId") Long sellerId);

    /**
     * Search seller's coupons by code or description
     */
    @Query("""
        SELECT c FROM Coupon c 
        WHERE c.seller.id = :sellerId
        AND (LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.createdAt DESC
    """)
    Page<Coupon> searchSellerCoupons(@Param("sellerId") Long sellerId, 
                                     @Param("keyword") String keyword, 
                                     Pageable pageable);
}
