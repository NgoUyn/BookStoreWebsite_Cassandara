package com.example.bookstore.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthLoginRequest {

    @NotBlank(message = "Email dang nhap khong duoc de trong")
    @Size(max = 100, message = "Email dang nhap toi da 100 ky tu")
    @Pattern(
        regexp = "^[A-Za-z0-9._%+-]+@(gmail|mail|email|outlook)\\.(com|vn|edu\\.vn|net)$",
        message = "Email dang nhap khong dung dinh dang"
    )
    private String username;

    @NotBlank(message = "Mat khau khong duoc de trong")
    @Size(min = 1, max = 72, message = "Mat khau phai tu 1 den 72 ky tu")
    @Pattern(
        regexp = "^\\S(?:.*\\S)?$",
        message = "Mat khau khong duoc chi gom khoang trang"
    )
    @JsonAlias({"passwordHash", "password"})
    private String password;
}
