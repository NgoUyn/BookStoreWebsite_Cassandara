package com.example.bookstore.model;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.bookstore.model.enums.ApprovalStatus;

import java.time.LocalDateTime;
@Entity
@Table(name = "seller_shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerShop {
    @Id
    private Long id;
    
    @OneToOne(fetch = FetchType.EAGER)
    @MapsId
    @JoinColumn(name = "seller_id", referencedColumnName = "id", nullable = false, unique = true)
    private User seller;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, name = "shop_name")
    private String shopName;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;
    @Column(name = "city", nullable = false)
    private String city;
    @Column(name = "province", nullable = true)
    private String province;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 50, columnDefinition = "NVARCHAR(50)")
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Builder.Default
    @Column(name = "follower_count")
    private Integer followerCount = 0;

    @Builder.Default
    @Column(name = "rating")
    private Double rating = 0.0;

    @Builder.Default
    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
