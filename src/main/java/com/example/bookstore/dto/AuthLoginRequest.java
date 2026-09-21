package com.example.bookstore.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthLoginRequest {

    @NotBlank(message = "Email dang nhap khong duoc de trong")
    @Size(max = 100, message = "Email dang nhap toi da 100 ky tu")
    // Truoc day dung @Pattern gioi han domain (gmail|mail|email|outlook + com|vn|edu.vn|net)
    // -> TAI KHOAN DANG KY BANG DOMAIN KHAC (vi du @yahoo.com, @example.com) KHONG BAO GIO
    //    DANG NHAP DUOC (dang ky van 200 vi AuthRegisterRequest chi dung @Email).
    // Nay dung @Email cho khop voi buoc dang ky va voi form login (type="email").
    @Email(message = "Email dang nhap khong dung dinh dang")
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
