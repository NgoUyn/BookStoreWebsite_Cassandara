package com.example.bookstore.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 100, message = "Email toi da 100 ky tu")
    @Pattern(
        regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$",
        message = "Email khong dung dinh dang"
    )
    private String username;

    @Size(max = 255, message = "Ten shop toi da 255 ky tu")
    @Pattern(
        regexp = "^$|^[A-Za-z0-9 .,'&()_\\\\-]{1,255}$",
        message = "Ten shop chua ky tu khong hop le"
    )
    private String shopName;

    @Size(max = 500, message = "Dia chi toi da 500 ky tu")
    @Pattern(
        regexp = "^$|^[A-Za-z0-9 .,#'&()_\\\\-/]{1,500}$",
        message = "Dia chi chua ky tu khong hop le"
    )
    private String shopAddress;

    @Size(max = 3000000, message = "Avatar qua lon")
    @Pattern(
        regexp = "^$|^data:image/(png|jpeg|jpg|gif|webp);base64,[A-Za-z0-9+/=\\r\\n]+$",
        message = "Avatar phai la data URL hinh anh hop le"
    )
    private String avatarUrl;

    @Size(max = 20, message = "Toi da 20 the loai yeu thich")
    private List<Long> favoriteCategoryIds;
}
