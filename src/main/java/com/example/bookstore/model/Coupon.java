package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code coupons} (voucher).
 *
 * <p>SQL Server phai dung {@code LOWER(code) = LOWER(:code)} nen khong tan dung
 * duoc index; MongoDB dung UNIQUE INDEX voi COLLATION strength 2
 * (xem db/mongo/02_indexes.js - uq_coupons_code_ci) => tim ma khong phan biet
 * hoa/thuong van nhanh.</p>
 */
@Document(collection = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private String code;

    /** null = voucher cua san (GLOBAL); co gia tri = voucher cua seller. */
    private Long sellerId;

    private String description;

    private CouponType type;

    @Field("amount")
    private Integer amount;

    private Integer minOrderAmount;
    private Double maxDiscountAmount;
    private LocalDateTime startDate;
    private LocalDateTime expiresAt;
    private Integer totalQuantity;
    private Integer usedCount;
    private Integer perUserLimit;

    @Field("isActive")
    private boolean isActive;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;

    public enum CouponType {
        FIXED,    // giam so tien co dinh
        PERCENT   // giam theo phan tram
    }

    /** Voucher con hieu luc tai thoi diem goi. */
    public boolean isValid() {
        if (!isActive) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return false;
        }
        return totalQuantity == null || totalQuantity < 0 || usedCount == null || usedCount < totalQuantity;
    }

    public boolean canApplyToOrder(Integer orderAmount) {
        if (!isValid()) {
            return false;
        }
        return minOrderAmount == null || orderAmount >= minOrderAmount;
    }

    public Integer calculateDiscount(Integer orderAmount) {
        int discount;
        if (type == CouponType.FIXED) {
            discount = Math.min(amount, orderAmount);
        } else {
            discount = (int) (orderAmount * amount / 100.0);
        }
        if (maxDiscountAmount != null && discount > maxDiscountAmount) {
            discount = maxDiscountAmount.intValue();
        }
        return discount;
    }
}
