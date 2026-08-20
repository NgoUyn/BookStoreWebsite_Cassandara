package com.example.bookstore.dto;

import lombok.Data;

@Data
public class SeedRequest {
    private Boolean seedCategories;
    private Boolean seedUsers;
    private Boolean seedBooks;
    private Boolean includeAi;
    private Integer maxBooks;
}
