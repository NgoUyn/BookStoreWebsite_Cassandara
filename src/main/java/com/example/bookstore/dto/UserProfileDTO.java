package com.example.bookstore.dto;

import com.example.bookstore.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import com.example.bookstore.dto.SellerShopResponse;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String bio;
    private String avatarUrl;
    private SellerShopResponse sellerShop;

    public static UserProfileDTO fromEntity(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    public void applyToEntity(User user) {
        if (this.firstName != null) user.setFirstName(this.firstName);
        if (this.lastName != null) user.setLastName(this.lastName);
        if (this.email != null) user.setEmail(this.email);
        if (this.phone != null) user.setPhone(this.phone);
        if (this.dateOfBirth != null) user.setDateOfBirth(this.dateOfBirth);
        if (this.gender != null) user.setGender(this.gender);
        if (this.bio != null) user.setBio(this.bio);
        if (this.avatarUrl != null) user.setAvatarUrl(this.avatarUrl);
    }
}
