package com.example.bookstore.controller;

import com.example.bookstore.model.Category;
import com.example.bookstore.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories") // Đường dẫn gốc cho các API của Thể loại
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    // API lấy toàn bộ danh sách thể loại
    @GetMapping
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }

    // API thêm một thể loại mới
    @PostMapping
    public Category createCategory(@RequestBody Category category) {
        return categoryService.addCategory(category);
    }
}