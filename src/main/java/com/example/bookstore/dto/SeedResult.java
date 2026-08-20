package com.example.bookstore.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeedResult {
    private int categoriesAdded;
    private int categoriesUpdated;
    private int usersAdded;
    private int booksAdded;
    private int booksSkipped;
    private boolean aiEnqueued;
    private List<String> warnings;
}
