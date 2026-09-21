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
 * Collection {@code user_security_events} - nhat ky bao mat.
 * Khong embed vao users (tang vo han); TTL 180 ngay o tang script.
 */
@Document(collection = "user_security_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityEvent implements SequencedDocument {

    @Id
    private Long id;

    private Long userId;

    private String eventType;
    private String eventDescription;
    private String ipAddress;
    private String userAgent;

    @Field("createdAt")
    private LocalDateTime createdAt;
}
