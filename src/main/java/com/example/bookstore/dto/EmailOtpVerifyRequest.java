package com.example.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailOtpVerifyRequest {
    @NotBlank(message = "Email khong duoc de trong")
    @Email(message = "Email khong dung dinh dang")
    @Size(max = 100, message = "Email toi da 100 ky tu")
    private String email;

    @NotBlank(message = "OTP khong duoc de trong")
    @Pattern(regexp = "^[0-9]{4,6}$", message = "OTP khong hop le")
    private String otp;
}
