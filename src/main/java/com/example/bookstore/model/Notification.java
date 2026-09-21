package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.enums.NotificationPriority;
import com.example.bookstore.model.enums.NotificationType;
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
 * Collection {@code notifications} - thong bao nguoi dung.
 *
 * <p>THAM CHIEU {@code userId} (khong embed vao users vi so luong thong bao
 * tang vo han) + TTL index {@code expiresAt} de MongoDB TU DONG don thong bao
 * cu (khong can job nhu ban SQL Server).</p>
 */
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification implements SequencedDocument {

    @Id
    private Long id;

    @Indexed
    private Long userId;

    private NotificationType type;

    private String title;
    private String message;

    /**
     * Payload duoi dang chuoi JSON (giu nguyen de khong phai sua DTO/JS).
     * Ban NoSQL dang "chuan" co the luu BSON document ({@code payload}).
     */
    private String payloadJson;

    @Field("isRead")
    private Boolean isRead;

    private NotificationPriority priority;

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    /** TTL: MongoDB xoa khi het han (tang 90 ngay tu luc tao). */
    @Indexed
    private LocalDateTime expiresAt;

    /**
     * TUONG THICH (compat): code cu goi {@code notification.getUser().getId()}.
     * Collection notifications chi giu {@code userId} nen tra ve User "vo".
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public User getUser() {
        return userId == null ? null : User.builder().id(userId).build();
    }

    public void setUser(User user) {
        this.userId = user == null ? null : user.getId();
    }
}
