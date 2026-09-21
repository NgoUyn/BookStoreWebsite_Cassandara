package com.example.bookstore.repository;

import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.ApprovalStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repository AGGREGATE seller_shops. */
@Repository
public interface SellerShopRepository extends MongoRepository<SellerShop, Long> {

    Optional<SellerShop> findBySellerId(Long sellerId);

    Optional<SellerShop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySellerIdAndApprovalStatus(Long sellerId, ApprovalStatus approvalStatus);

    List<SellerShop> findByApprovalStatus(ApprovalStatus status);

    List<SellerShop> findByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus status);

    /** Tim shop theo tu khoa (ten shop) - khong phan biet hoa thuong. */
    @Query("{ 'shopName': { $regex: ?0, $options: 'i' } }")
    List<SellerShop> searchByShopName(String keyword);
}
