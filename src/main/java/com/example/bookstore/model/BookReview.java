package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT #6 - collection {@code reviews}.
 *
 * <p>LY DO la aggregate RIENG (khong embed vao books):
 * quan he 1-N KHONG TRAN + bi truy van cat ngang theo ca bookId va userId,
 * va can rang buoc unique (bookId,userId) o muc DB.</p>
 *
 * <p>EMBED ben trong review: images[], replies[] (toi da 20, dung
 * {@code $push + $slice}), moderation history, snapshot book{} + user{}.</p>
 */
@Document(collection = "reviews")
@CompoundIndex(name = "uq_reviews_book_user", def = "{'bookId': 1, 'userId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookReview implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    private Long bookId;

    /** Snapshot sach (extended reference) - tranh doc collection books khi hien thi review. */
    private BookRef book;

    private Long userId;

    /** Snapshot nguoi viet (JSON phai co user.username/user.id cho details-page.js). */
    private UserSnapshot user;

    private Long orderId;
    private Boolean isVerifiedPurchase;

    private Integer rating;
    private String comment;

    @Builder.Default
    private List<ReviewImage> images = new ArrayList<>();

    @Builder.Default
    private List<Reply> replies = new ArrayList<>();

    private Integer helpfulCount;

    /** Giu ten field cu cua SQL Server de khong phai sua admin UI. */
    @Field("isHidden")
    private boolean isHidden;

    private String moderationStatus;    // VISIBLE | HIDDEN
    private String moderationReason;
    private Long moderationByUserId;
    private LocalDateTime moderationAt;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;

    // ======================== Tien ich =====================================

    @JsonIgnore
    public String getBookTitle() {
        return book == null ? null : book.getTitle();
    }

    @JsonIgnore
    public String getUsername() {
        return user == null ? null : user.getUsername();
    }

    /**
     * TUONG THICH (compat): code cu goi {@code review.setBook(book)}.
     * Nay luu snapshot {@code BookRef} (id, title, imageUrl...) - khong embed
     * ca document sach vao review.
     */
    public void setBook(Book book) {
        if (book == null) {
            this.book = null;
            this.bookId = null;
            return;
        }
        this.bookId = book.getId();
        this.book = BookRef.builder()
                .id(book.getId())
                .title(book.getTitle())
                .imageUrl(book.getImageUrl())
                .sellerId(book.getSellerId())
                .categoryId(book.getCategoryId())
                .categoryName(book.getCategoryName())
                .build();
    }

    /** TUONG THICH: {@code review.setUser(user)} -> luu userId + snapshot. */
    public void setUser(User user) {
        this.userId = user == null ? null : user.getId();
        this.user = UserSnapshot.of(user);
    }

    // ======================================================================
    // EMBED: snapshot sach
    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookRef {
        private Long id;
        private String title;
        private String imageUrl;
        private Long sellerId;
        private Long categoryId;
        private String categoryName;
    }

    // ======================================================================
    // EMBED: anh cua review (file that luu trong GridFS)
    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewImage {
        private Object fileId;
        private String url;
    }

    // ======================================================================
    // EMBED: phan hoi cua shop (toi da 20, dung $slice - $push)
    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reply {
        private Long replyId;
        private Long userId;
        private Actor user;
        private String content;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor {
        private String username;
        private String role;
    }
}
