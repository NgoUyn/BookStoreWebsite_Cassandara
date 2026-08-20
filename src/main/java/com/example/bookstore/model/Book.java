package com.example.bookstore.model;

import com.example.bookstore.model.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity //danh dau day la 1 bang trong DB
@Table(name="books")
@Data // tu dong tao Getter,Setter,toString,equals,hashCode
@ToString
@EqualsAndHashCode
@NoArgsConstructor // Auto tạo Constructor không tham số
@AllArgsConstructor // Auto tao Constructor co tham so
@DynamicUpdate // hỗ trợ để update động

public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto tang id theo index
    private Long id;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(nullable = false)
    private String author;
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;
    private Double price;
    private Integer stockQuantity; //
    @Column(length = 500)
    private String imageUrl; // link ảnh lấy từ CSV
    @Column(name = "medium_image_url")
    private String mediumImageUrl;
    @Column(name = "largeimage_url")
    private String largeimageUrl;
    @Column(name = "publisher")
    private String publisher;  // Nhà xuất bản
    private Integer publishYear; // Năm xuất bản
    @Column(name = "average_rating")
    private Double averageRating;
    @Column(name = "discount_amount")
    private Integer discountAmount = 0;

    @ManyToOne
    @JoinColumn(name = "category_id") // Tên cột khóa ngoại trong SSMS
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("books")
    private Category category;

    // Transient field để hỗ trợ JSON deserialization từ categoryId
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("categoryId")
    private Long categoryId;

    // Getter/Setter cho categoryId để Jackson có thể deserialize
    public Long getCategoryId() {
        return category != null ? category.getId() : categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    // nhiều người seller có thể bán cùng 1 cuốn sách
    @ManyToOne(fetch = FetchType.LAZY) // để không cần phải tải hết
    @JoinColumn(name = "seller_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "books", "roles"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private ApprovalStatus approvalStatus; // trạng thái sách phải thông qua admin đối với sách của seller thông qua emum ApprovalStatus

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false;

    @PrePersist
    public void onCreate() {
        if (approvalStatus == null) {
            approvalStatus = ApprovalStatus.PENDING;
        }
    }
}
