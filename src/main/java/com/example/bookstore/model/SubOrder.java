package com.example.bookstore.model;

import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "Don con" theo tung nha ban - NHUNG trong {@code orders.subOrders[]}
 * (SQL Server truoc day la bang {@code sub_orders} + quan he voi order_items).
 *
 * <p>Moi subOrder chua: seller snapshot, buyer snapshot (de dashboard seller
 * hien thi ten nguoi mua ma khong can doc theo order cha), items[],
 * statusHistory[] va version (optimistic locking).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrder {

    /** id cua subOrder trong toan he thong (cap tu counters "sub_orders"). */
    @org.springframework.data.mongodb.core.mapping.Field("subOrderId")
    private Long id;

    private Long orderId;

    private Long sellerId;

    /** Extended reference: thong tin shop/nguoi ban tai thoi diem dat hang. */
    private UserSnapshot seller;

    /** Extended reference: thong tin nguoi mua (dung cho danh sach cua seller). */
    private UserSnapshot buyer;

    private OrderStatus status;

    private Double subTotal;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    private List<StatusEntry> statusHistory = new ArrayList<>();

    private String voucherCode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /** Ghi 1 dong lich su trang thai (thay cho bang audit rieng). */
    public void addStatusEntry(OrderStatus newStatus, String note) {
        if (statusHistory == null) {
            statusHistory = new ArrayList<>();
        }
        statusHistory.add(new StatusEntry(newStatus, LocalDateTime.now(), note));
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /** Tong so luong san pham trong don con. */
    @JsonIgnore
    public int getTotalQuantity() {
        if (items == null) {
            return 0;
        }
        return items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
    }

    /** Mo ta ngan gon cac san pham - dung cho danh sach cua seller/admin. */
    @JsonIgnore
    public String getItemSummary() {
        if (items == null || items.isEmpty()) {
            return "";
        }
        String first = items.get(0).getTitle();
        return items.size() == 1 ? first : first + " + " + (items.size() - 1) + " san pham khac";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusEntry {
        private OrderStatus status;
        private LocalDateTime at;
        private String note;
    }

    // ======================== Getter/SETTER tuong thich =====================

    /**
     * TUONG THICH (compat): code cu goi {@code subOrder.getParentOrder()}.
     * Tra ve Order "vo" (chi id + buyer snapshot) vi subOrder nam TRONG
     * document order - khong con quan he 2 chieu nhu JPA.
     */
    @JsonIgnore
    public Order getParentOrder() {
        if (orderId == null) {
            return null;
        }
        return Order.builder().id(orderId).buyer(buyer).build();
    }

    public void setParentOrder(Order order) {
        if (order == null) {
            return;
        }
        this.orderId = order.getId();
        if (this.buyer == null) {
            this.buyer = order.getBuyer();
        }
    }

    /** TUONG THICH: code cu truyen entity User - nay luu sellerId + snapshot. */
    public void setSeller(User user) {
        this.sellerId = user == null ? null : user.getId();
        this.seller = UserSnapshot.of(user);
    }

    public void setBuyerUser(User user) {
        this.buyer = UserSnapshot.of(user);
    }
}
