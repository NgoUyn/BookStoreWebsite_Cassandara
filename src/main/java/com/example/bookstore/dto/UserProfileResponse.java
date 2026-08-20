package com.example.bookstore.dto;

import com.example.bookstore.model.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String username;
    private UserRole role;
    private String shopName;
    private String shopAddress;
    private String avatarUrl;
    private List<Long> favoriteCategoryIds;
}
