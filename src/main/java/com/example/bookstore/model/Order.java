package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.model.enums.PaymentMethod;
import com.example.bookstore.model.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT #4 - collection {@code orders}.
 *
 * <p>Gop 3 bang SQL Server ({@code orders_master} + {@code sub_orders} +
 * {@code order_items}) vao MOT document: 1 lan dat hang = 1 insert ATOMIC,
 * doc chi tiet don = 1 lan doc document (khong join, khong N+1).</p>
 *
 * <p>EMBED: subOrders[].items[], shipping{}, payment{}, buyer snapshot.
 * THAM CHIEU: payment_transactions, order_returns (co vong doi rieng).</p>
 */
@Document(collection = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private String orderCode;

    @Indexed
    private Long buyerId;

    /** Snapshot nguoi mua - tranh phai doc collection users khi hien thi/seller xem. */
    private UserSnapshot buyer;

    private Double itemsTotal;

    @Field("discountAmount")
    private Double discountAmount;

    private Double shippingFee;

    @Field("totalAmount")
    private Double totalAmount;

    private String couponCode;

    private Shipping shipping;

    private Payment payment;

    /** Trang thai tong hop cua don (lay tu subOrder dau tien neu nhieu seller). */
    private OrderStatus status;

    @Builder.Default
    private List<SubOrder> subOrders = new ArrayList<>();

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Field("isDeleted")
    private Boolean isDeleted;

    /**
     * Optimistic locking cua MongoDB: hai request cung sua 1 don hang se bi
     * chan (thay cho @Version tren bang sub_orders cua ban SQL).
     */
    @Version
    private Long version;

    private Integer schemaVersion;

    // ======================================================================
    // EMBED: thong tin giao hang (SQL: cot trong orders_master)
    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Shipping {

        private String address;
        private String recipient;
        private String phone;
        private String method;
        private Double fee;
        private String trackingCode;
    }

    // ======================================================================
    // EMBED: thong tin thanh toan (summary; chi tiet o payment_transactions)
    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payment {

        private PaymentMethod method;
        private PaymentStatus status;
        private Double amount;
        private LocalDateTime paidAt;
    }

    // ======================== Tien ich =====================================

    /** Dia chi giao hang (giu getter cu cua ban SQL). */
    @JsonIgnore
    public String getShippingAddress() {
        return shipping == null ? null : shipping.getAddress();
    }

    public void setShippingAddress(String address) {
        if (shipping == null) {
            shipping = new Shipping();
        }
        shipping.setAddress(address);
    }

    /** Ten nguoi mua (cho DTO cua seller/admin). */
    @JsonIgnore
    public String getBuyerName() {
        return buyer == null ? null : buyer.getFullName();
    }

    @JsonIgnore
    public String getBuyerUsername() {
        return buyer == null ? null : buyer.getUsername();
    }

    public SubOrder findSubOrderById(Long subOrderId) {
        if (subOrders == null || subOrderId == null) {
            return null;
        }
        return subOrders.stream()
                .filter(so -> subOrderId.equals(so.getId()))
                .findFirst()
                .orElse(null);
    }

    /** Trang thai tong hop: tat ca subOrder cung trang thai thi lay trang thai do. */
    public OrderStatus aggregateStatus() {
        if (subOrders == null || subOrders.isEmpty()) {
            return status;
        }
        OrderStatus first = subOrders.get(0).getStatus();
        boolean same = subOrders.stream().allMatch(so -> so.getStatus() == first);
        return same ? first : null;
    }
}
