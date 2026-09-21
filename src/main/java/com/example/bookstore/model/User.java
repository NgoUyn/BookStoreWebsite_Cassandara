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

    /**
     * Trang thai hoat dong. MAC DINH = true cho tai khoan moi.
     *
     * <p>Phai co {@code @Builder.Default}: Lombok {@code @Builder} BO QUA gia tri
     * khoi tao cua field neu khong danh dau => truoc day moi tai khoan dang ky moi
     * deu bi luu {@code isActive=false} va bi AuthController chan dang nhap
     * (403 "Tai khoan bi tu choi dang nhap").</p>
     */
    @Field("isActive")
    @Builder.Default
    private boolean isActive = true;

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

    // ======================= LOP TUONG THICH (adapter) ======================

    /**
     * TUONG THICH: code cu goi {@code user.getFavoriteCategories()} (quan he
     * ManyToMany bang trung gian). Nay chi luu {@code favoriteCategoryIds[]}
     * nen tra ve cac Category "vo" chi co id.
     */
    @JsonIgnore
    public java.util.Set<Category> getFavoriteCategories() {
        java.util.Set<Category> set = new java.util.LinkedHashSet<>();
        if (favoriteCategoryIds != null) {
            for (Long categoryId : favoriteCategoryIds) {
                set.add(Category.builder().id(categoryId).build());
            }
        }
        return set;
    }

    public void setFavoriteCategories(java.util.Set<Category> categories) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        if (categories != null) {
            for (Category category : categories) {
                if (category != null && category.getId() != null) {
                    ids.add(category.getId());
                }
            }
        }
        this.favoriteCategoryIds = ids;
    }
}
