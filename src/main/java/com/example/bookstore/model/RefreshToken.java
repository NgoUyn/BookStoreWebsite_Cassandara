package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Collection {@code refresh_tokens}.
 * TTL index tren {@code expiryDate} => MongoDB tu xoa token het han
 * (ban SQL Server phai chay job don rac).
 */
@Document(collection = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken implements SequencedDocument {

    @Id
    private Long id;

    private Long userId;

    private String token;

    private Instant expiryDate;

    /**
     * TUONG THICH (compat): code cu goi {@code refreshToken.getUser()}.
     * Collection refresh_tokens chi giu {@code userId}.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public User getUser() {
        return userId == null ? null : User.builder().id(userId).build();
    }

    public void setUser(User user) {
        this.userId = user == null ? null : user.getId();
    }
}
