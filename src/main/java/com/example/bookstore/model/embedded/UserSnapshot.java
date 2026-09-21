package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SNAPSHOT (extended reference) cua User - duoc NHUNG vao cac aggregate khac:
 * Book.seller, Order.buyer, SubOrder.seller, Review.user, SellerShop.seller.
 *
 * <p>Ly do: tranh phai $lookup/N+1 query khi hien thi, dong thoi giu nguyen
 * duong dan JSON ma frontend dang doc ({@code book.seller.id},
 * {@code book.seller.username}, {@code book.seller.shopName}).</p>
 *
 * <p>Danh doi da ghi trong docs\mongodb\DESIGN.md muc 8.4: khi seller doi ten
 * phai cap nhat snapshot bang updateMany (SnapshotUpdater).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshot {

    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String shopName;
    private String email;
    private String phone;
    private String avatarUrl;

    public static UserSnapshot of(com.example.bookstore.model.User user) {
        if (user == null) {
            return null;
        }
        String fullName = ((user.getLastName() == null ? "" : user.getLastName()) + " "
                + (user.getFirstName() == null ? "" : user.getFirstName())).trim();
        return UserSnapshot.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName.isEmpty() ? user.getUsername() : fullName)
                .shopName(user.getShopName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /**
     * TUONG THICH (compat): dung lai User tu snapshot khi nghiep vu can doi tuong
     * User (vi du gui email) ma chi co snapshot trong document.
     */
    public com.example.bookstore.model.User toUser() {
        return com.example.bookstore.model.User.builder()
                .id(id)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .shopName(shopName)
                .avatarUrl(avatarUrl)
                .build();
    }
}
