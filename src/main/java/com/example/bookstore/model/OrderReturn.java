package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code order_returns} - yeu cau tra hang/hoan tien.
 *
 * <p>THAM CHIEU order/subOrder/orderItem (khong embed) vi co quy trinh
 * xu ly rieng (PENDING -> APPROVED -> REFUNDED) va duoc admin/seller
 * truy van theo trang thai.</p>
 */
@Document(collection = "order_returns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturn implements SequencedDocument {

    @Id
    private Long id;

    private Long userId;
    private Long orderId;
    private Long subOrderId;
    private Long orderItemId;

    private Integer quantityReturned;

    /** DEFECTIVE | WRONG_ITEM | CHANGE_MIND | OTHER */
    private String reason;

    /** PENDING | APPROVED | REJECTED | REFUNDED */
    private String status;

    @Field("createdAt")
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
