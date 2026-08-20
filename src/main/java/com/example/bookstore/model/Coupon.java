package com.example.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;  // "BOOKOM15K", "SAVE10", etc.

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private User seller;  // Seller who owns this voucher (NULL = global/admin coupon)

    @Column(length = 500)
    private String description;  // "Giảm 15k cho đơn hàng"

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponType type;  // FIXED or PERCENT

    @Column(name = "discount_value", nullable = false)
    private Integer amount;  // 15000 (for FIXED) or 10 (for PERCENT)

    @Column(name = "min_order_value")
    private Integer minOrderAmount;  // Minimum order value to use coupon

    @Column(name = "max_discount_amount")
    private Double maxDiscountAmount;  // Maximum discount amount (for PERCENT type)

    @Column(name = "start_date")
    private LocalDateTime startDate;  // Start date when coupon becomes valid

    @Column(name = "end_date")
    private LocalDateTime expiresAt;  // Expiration date

    @Column(name = "usage_limit", nullable = false)
    @Builder.Default
    private Integer totalQuantity = -1;  // -1 = unlimited

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;  // Already used count

    @Column(nullable = false, columnDefinition = "BIT DEFAULT 1")
    @Builder.Default
    private boolean isActive = true;  // true = usable, false = disabled

    @Column(nullable = false, columnDefinition = "DATETIME DEFAULT GETDATE()")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt;

    public enum CouponType {
        FIXED,    // Fixed amount discount (e.g., -15000 VND)
        PERCENT   // Percentage discount (e.g., -10%)
    }

    /**
     * Check if coupon is still valid
     */
    public boolean isValid() {
        if (!isActive) return false;
        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate)) return false;  // Not yet started
        if (expiresAt != null && now.isAfter(expiresAt)) return false;  // Expired
        if (totalQuantity > 0 && usedCount >= totalQuantity) return false;  // Out of usage
        return true;
    }

    /**
     * Check if coupon can be applied to this order amount
     */
    public boolean canApplyToOrder(Integer orderAmount) {
        if (!isValid()) return false;
        if (minOrderAmount != null && orderAmount < minOrderAmount) return false;
        return true;
    }

    /**
     * Calculate discount amount for given order value
     */
    public Integer calculateDiscount(Integer orderAmount) {
        int discount;
        if (type == CouponType.FIXED) {
            discount = Math.min(amount, orderAmount);
        } else {  // PERCENT
            discount = (int) (orderAmount * amount / 100.0);
        }
        
        // Apply maxDiscountAmount cap if set
        if (maxDiscountAmount != null && discount > maxDiscountAmount) {
            discount = maxDiscountAmount.intValue();
        }
        
        return discount;
    }
}
