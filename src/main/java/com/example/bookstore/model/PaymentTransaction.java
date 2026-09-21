package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.enums.PaymentMethod;
import com.example.bookstore.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code payment_transactions} - giao dich thanh toan (VNPay...).
 *
 * <p>THAM CHIEU {@code orderId} (khong embed vao orders) vi can:
 * unique index {@code transactionCode} (chong trung giao dich) va TTL don
 * link VNPay het han - neu embed thi TTL se xoa ca don hang!</p>
 */
@Document(collection = "payment_transactions")
@CompoundIndex(name = "idx_pt_order_status", def = "{'orderId': 1, 'status': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction implements SequencedDocument {

    @Id
    private Long id;

    private Long orderId;

    private Long amount;

    private PaymentMethod method;

    private PaymentStatus status;

    /** Ma giao dich VNPay - UNIQUE (partial index, xem 02_indexes.js). */
    private String transactionCode;

    private String paymentUrl;
    private String responseCode;
    private String responseMessage;
    private String failureReason;

    @Field("createdAt")
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    /** TTL: link thanh toan het han (chi ap dung khi status = PENDING). */
    private LocalDateTime expiredAt;

    /**
     * TUONG THICH (compat): code cu goi {@code transaction.getOrder().getId()}.
     * PaymentTransaction la collection THAM CHIEU (chi giu orderId), nen tra ve
     * Order "vo" chi co id - khong doc lai collection orders.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Order getOrder() {
        return orderId == null ? null : Order.builder().id(orderId).build();
    }
}
