package com.example.bookstore.model;

import com.example.bookstore.model.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity // Đánh dấu đây là 1 bảng trong DB
@Table(name="users") // Tên bảng dưới Database sẽ là 'users'
@Data
@ToString
@EqualsAndHashCode
@NoArgsConstructor // Tự tạo Constructor không tham số
@AllArgsConstructor // Tự tạo Constructor có đủ tham số
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Tự động tăng ID
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // Profile Information
    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender; // MALE, FEMALE, OTHER

    @Column(length = 500)
    private String bio;

    // Seller Information
    @Column(length = 255)
    private String shopName;

    @Column(length = 500)
    private String shopAddress;

    @Lob
    @Column(name = "avatar_url")
    private String avatarUrl;

    @ManyToMany
    @JoinTable(
        name = "user_favorite_categories",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Category> favoriteCategories = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
        name = "user_wishlist_books",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "book_id")
    )
    @OrderBy("id DESC")
    @JsonIgnore
    @Builder.Default
    private Set<Book> wishlistBooks = new LinkedHashSet<>();

    @OneToMany(mappedBy = "seller")
    @JsonIgnore
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Book> books = new ArrayList<>();

    @OneToMany(mappedBy = "seller")
    @JsonIgnore
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubOrder> subOrders = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<UserAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<UserSecurityEvent> securityEvents = new ArrayList<>();

    @OneToOne(mappedBy = "buyer")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cart cart;

    @Column(nullable = false, columnDefinition = "BIT DEFAULT 1")
    @Builder.Default
    private boolean isActive = true;  // true = hoạt động, false = khóa

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (role == null) {
            role = UserRole.BUYER;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
