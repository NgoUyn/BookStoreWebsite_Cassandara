package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
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
 * Collection {@code notification_deliveries} - tung lan gui thong bao.
 *
 * <p>THAM CHIEU {@code notificationId} (khong embed vao notification) vi:
 * (1) lich su retry tang vo han, (2) queue worker phai POLL theo cap
 * {@code (status, nextRetryAt)} tren toan bo collection.</p>
 */
@Document(collection = "notification_deliveries")
@CompoundIndex(name = "idx_nd_pending_retry", def = "{'status': 1, 'nextRetryAt': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDelivery implements SequencedDocument {

    @Id
    private Long id;

    private Long notificationId;

    private Long userId;

    /** SSE | EMAIL | PUSH | SMS */
    private String channel;

    private DeliveryStatus status;

    private Integer attemptCount;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime sentAt;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    public enum DeliveryStatus {
        PENDING, SENT, FAILED, DROPPED
    }
}
