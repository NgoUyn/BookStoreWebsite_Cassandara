package com.example.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseLoginRequest {
    @NotBlank(message = "Firebase ID Token không được để trống")
    private String idToken;
}
