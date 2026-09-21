package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.embedded.MlProfile;
import com.example.bookstore.model.embedded.SellerProfile;
import com.example.bookstore.model.embedded.UserStats;
import com.example.bookstore.model.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT #1 - collection {@code users}.
 *
 * <p>Gop 5 bang cua SQL Server thanh 1 document:
 * {@code users} + {@code user_addresses[]} + {@code user_favorite_categories[]}
 * + {@code user_wishlist_books[]} + {@code customer_ml} (partial).</p>
 *
 * <p>Thanh phan EMBED (bounded): addresses (1-5), wishlistBookIds (<=200),
 * favoriteCategoryIds, seller (SellerProfile), stats (UserStats), ml (MlProfile).</p>
 */
@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private String username;

    @JsonIgnore
    @Field("passwordHash")
    private String passwordHash;

    @Indexed
    private UserRole role;

    // ======================== Ho so ca nhan (embed) ========================
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String bio;
    private String avatarUrl;

    // ======================== Thong tin nha ban (embed) =====================
    private String shopName;
    private String shopAddress;
    private SellerProfile seller;

    // ======================== Cac mang con (embed) ==========================
    @Builder.Default
    private List<UserAddress> addresses = new ArrayList<>();

    /** ID danh muc yeu thich (thay bang user_favorite_categories). */
    @Builder.Default
    private List<Long> favoriteCategoryIds = new ArrayList<>();

    /** ID sach yeu thich (thay bang user_wishlist_books). */
    @Builder.Default
    private List<Long> wishlistBookIds = new ArrayList<>();

    /** Thong ke tich luy - tinh san bang $merge (materialized). */
    private UserStats stats;

    /** Ho so ML (churn/RFM) - tinh san bang job + $merge. */
    private MlProfile ml;

    @Field("isActive")
    private boolean isActive;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;

    @Override
    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    // ======================== Tien ich ======================================

    /** Ten hien thi day du (dung cho snapshot/notification). */
    @JsonIgnore
    public String getDisplayName() {
        String full = ((lastName == null ? "" : lastName) + " "
                + (firstName == null ? "" : firstName)).trim();
        return full.isEmpty() ? username : full;
    }
}
