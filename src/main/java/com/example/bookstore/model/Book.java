package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.embedded.BookImage;
import com.example.bookstore.model.embedded.BookStats;
import com.example.bookstore.model.embedded.BoughtTogether;
import com.example.bookstore.model.embedded.RatingSummary;
import com.example.bookstore.model.embedded.ReviewSnippet;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT #2 - collection {@code books}.
 *
 * <p>EMBED: images{}, rating{} (read model), stats{} (counter), tags[],
 * boughtTogether[] (subset top 10), topReviews[] (subset 3), seller snapshot.
 * THAM CHIEU: categoryId/categoryName (denorm), sellerId, review that nam o
 * collection {@code reviews}.</p>
 *
 * <p>TUONG THICH FRONTEND: {@code imageUrl}/{@code averageRating} duoc expose
 * bang getter tinh toan (du lieu that nam trong images{}/rating{}) de khong
 * phai sua 21 file JS + template.</p>
 */
@Document(collection = "books")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed
    private String title;

    @Indexed
    private String author;

    private String description;
    private Double price;
    private Integer stockQuantity;

    /** Anh bia (GridFS id khi anh duoc luu trong MongoDB). */
    private BookImage images;
    private Object coverFileId;

    private String publisher;
    private Integer publishYear;
    private String isbn;

    @Field("discountAmount")
    private Integer discountAmount;

    /** Gia sau giam - tinh san de sort/filter bang index. */
    private Double finalPrice;

    // ======================== Danh muc (denormalized) =======================
    @Indexed
    private Long categoryId;
    private String categoryName;
    private List<Long> categoryPath;

    // ======================== Nha ban (extended reference) ==================
    @Indexed
    private Long sellerId;
    private UserSnapshot seller;

    // ======================== Du lieu phan tich (read model) ================
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private RatingSummary rating;

    private BookStats stats;

    @Builder.Default
    private List<ReviewSnippet> topReviews = new ArrayList<>();

    @Builder.Default
    private List<BoughtTogether> boughtTogether = new ArrayList<>();

    // ======================== Trang thai ====================================
    @Field("approvalStatus")
    private ApprovalStatus approvalStatus;

    @Field("isActive")
    private boolean isActive;

    @Field("isPinned")
    private boolean isPinned;

    private Integer schemaVersion;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    // ======================== Getter/SETTER tuong thich frontend ============

    /** Frontend dung {@code book.imageUrl} (ten field cua ban SQL). */
    public String getImageUrl() {
        return images == null ? null : images.getThumbnail();
    }

    public void setImageUrl(String imageUrl) {
        if (images == null) {
            images = new BookImage();
        }
        images.setThumbnail(imageUrl);
    }

    public String getMediumImageUrl() {
        return images == null ? null : images.getMedium();
    }

    public void setMediumImageUrl(String url) {
        if (images == null) {
            images = new BookImage();
        }
        images.setMedium(url);
    }

    public String getLargeimageUrl() {
        return images == null ? null : images.getLarge();
    }

    public void setLargeimageUrl(String url) {
        if (images == null) {
            images = new BookImage();
        }
        images.setLarge(url);
    }

    /** Frontend dung {@code book.averageRating} de hien thi so sao. */
    public Double getAverageRating() {
        return rating == null ? null : rating.getAvg();
    }

    public void setAverageRating(Double averageRating) {
        if (rating == null) {
            rating = new RatingSummary();
        }
        rating.setAvg(averageRating);
    }

    @JsonIgnore
    public Integer getSoldCount() {
        return stats == null ? 0 : stats.getSoldCount();
    }

    @JsonIgnore
    public Integer getViewCount() {
        return stats == null ? 0 : stats.getViewCount();
    }

    /** Gia hien thi = gia sau giam (fallback ve gia goc). */
    @JsonIgnore
    public Double getEffectivePrice() {
        if (finalPrice != null) {
            return finalPrice;
        }
        return price == null ? 0.0 : price;
    }
}
