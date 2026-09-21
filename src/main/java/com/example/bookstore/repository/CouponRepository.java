package com.example.bookstore.repository;

import com.example.bookstore.model.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository collection coupons.
 *
 * <p>DIEM MANH so voi SQL Server: tim ma KHONG phan biet hoa/thuong bang
 * COLLATION tren unique index ({@code uq_coupons_code_ci}) thay vi
 * {@code LOWER(code) = LOWER(:code)} (khong dung duoc index).</p>
 */
@Repository
public interface CouponRepository extends MongoRepository<Coupon, Long> {

    @Query(value = "{ 'code': ?0 }", collation = "{ 'locale': 'en', 'strength': 2 }")
    Optional<Coupon> findByCodeIgnoreCase(String code);

    @Query(value = "{ 'code': ?0 }", collation = "{ 'locale': 'en', 'strength': 2 }", exists = true)
    boolean existsByCodeIgnoreCase(String code);

    @Query(value = "{ 'code': ?0, 'sellerId': ?1 }", collation = "{ 'locale': 'en', 'strength': 2 }")
    Optional<Coupon> findByCodeIgnoreCaseAndSellerId(String code, Long sellerId);

    @Query(value = "{ 'code': ?0, 'sellerId': ?1 }", collation = "{ 'locale': 'en', 'strength': 2 }", exists = true)
    boolean existsByCodeIgnoreCaseAndSellerId(String code, Long sellerId);

    List<Coupon> findByIsActiveTrue();

    Page<Coupon> findByIsActive(boolean isActive, Pageable pageable);

    List<Coupon> findByExpiresAtBefore(LocalDateTime now);

    /** Voucher con hieu luc (dung $expr de so sanh field/tinh toan trong DB). */
    @Query("{ $expr: { $and: [ "
            + "{ $eq: ['$isActive', true] }, "
            + "{ $or: [ { $eq: ['$expiresAt', null] }, { $gt: ['$expiresAt', '$$NOW'] } ] }, "
            + "{ $or: [ { $lt: ['$totalQuantity', 0] }, { $lt: ['$usedCount', '$totalQuantity'] } ] } ] } }")
    List<Coupon> findAllValidCoupons();

    @Query("{ $and: [ { 'isActive': true }, "
            + "{ 'expiresAt': { $ne: null } }, "
            + "{ $expr: { $lte: [ '$expiresAt', { $dateAdd: { startDate: '$$NOW', unit: 'day', amount: 7 } } ] } } ] }")
    List<Coupon> findExpiringCoupons();

    @Query("{ 'description': { $regex: ?0, $options: 'i' } }")
    Page<Coupon> searchCoupons(String keyword, Pageable pageable);

    // --------------------------- Theo seller --------------------------------
    Page<Coupon> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    Page<Coupon> findBySellerIdAndIsActiveTrueOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    @Query("{ 'sellerId': ?1, $expr: { $and: [ "
            + "{ $eq: ['$isActive', true] }, "
            + "{ $or: [ { $eq: ['$startDate', null] }, { $lte: ['$startDate', '$$NOW'] } ] }, "
            + "{ $or: [ { $eq: ['$expiresAt', null] }, { $gt: ['$expiresAt', '$$NOW'] } ] }, "
            + "{ $or: [ { $lt: ['$totalQuantity', 0] }, { $lt: ['$usedCount', '$totalQuantity'] } ] } ] }, "
            + "'code': { $regex: ?0, $options: 'i' } }")
    Optional<Coupon> findValidVoucherForSeller(String code, Long sellerId);

    @Query("{ $expr: { $and: [ "
            + "{ $eq: ['$sellerId', ?0] }, "
            + "{ $eq: ['$isActive', true] }, "
            + "{ $or: [ { $eq: ['$startDate', null] }, { $lte: ['$startDate', '$$NOW'] } ] }, "
            + "{ $or: [ { $eq: ['$expiresAt', null] }, { $gt: ['$expiresAt', '$$NOW'] } ] }, "
            + "{ $or: [ { $lt: ['$totalQuantity', 0] }, { $lt: ['$usedCount', '$totalQuantity'] } ] } ] } }")
    List<Coupon> findAllValidVouchersForSeller(Long sellerId);

    @Query("{ 'sellerId': ?0, $or: [ { 'code': { $regex: ?1, $options: 'i' } }, "
            + "{ 'description': { $regex: ?1, $options: 'i' } } ] }")
    Page<Coupon> searchSellerCoupons(Long sellerId, String keyword, Pageable pageable);

    List<Coupon> findBySellerId(Long sellerId);

    long countBySellerIdAndIsActiveTrue(Long sellerId);
}
