package com.example.bookstore.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AuthRegisterRequest {

    @NotBlank(message = "Email dang ky khong duoc de trong")
    @Size(max = 100, message = "Email dang ky toi da 100 ky tu")
    @Email(message = "Email dang ky khong dung dinh dang")
    private String username;

    @NotBlank(message = "Mat khau khong duoc de trong")
    @Size(min = 8, max = 72, message = "Mat khau phai tu 8 den 72 ky tu")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$",
        message = "Mat khau phai co chu thuong, chu hoa, so va ky tu dac biet"
    )
    @JsonAlias({"passwordHash", "password"})
    private String password;

    @Size(max = 3000000, message = "Avatar qua lon")
    @Pattern(
        regexp = "^$|^data:image/(png|jpeg|jpg|gif|webp);base64,[A-Za-z0-9+/=\\r\\n]+$",
        message = "Avatar phai la data URL hinh anh hop le"
    )
    private String avatarUrl;

    @Size(max = 20, message = "Toi da 20 the loai yeu thich")
    private List<@Min(value = 1, message = "ID the loai khong hop le") Long> favoriteCategoryIds;
}
