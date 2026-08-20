package com.example.bookstore.repository;

import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerShopRepository extends JpaRepository<SellerShop, Long> {

    /**
     * Tìm kiếm shop dựa trên ID của Seller.
     * Vì quan hệ là 1:1, kết quả trả về là Optional để tránh NullPointerException.
     */
    Optional<SellerShop> findBySellerId(Long sellerId);

    /**
     * Kiểm tra sự tồn tại của Slug trong hệ thống.
     * Dùng để validation khi người dùng tạo shop hoặc đổi tên shop.
     */
    boolean existsBySlug(String slug);

    /**
     * Tìm kiếm shop bằng Slug để hiển thị trang chi tiết shop trên Frontend.
     */
    Optional<SellerShop> findBySlug(String slug);

    java.util.List<SellerShop> findByApprovalStatus(com.example.bookstore.model.enums.ApprovalStatus status);

    /**
     * Kiểm tra xem Seller đã có shop được duyệt hay chưa.
     * Dùng để kiểm tra quyền truy cập của Seller.
     */
    boolean existsBySellerIdAndApprovalStatus(Long sellerId, ApprovalStatus approvalStatus);
}
