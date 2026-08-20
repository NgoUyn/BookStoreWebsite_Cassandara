package com.example.bookstore.service;

import com.example.bookstore.model.Category;
import com.example.bookstore.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    // Lấy danh sách tất cả thể loại
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    // Thêm một thể loại mới
    public Category addCategory(Category category) {
        return categoryRepository.save(category);
    }

    // Cập nhật thể loại
    public Category updateCategory(Long id, Category dto) {
        Category existing = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        return categoryRepository.save(existing);
    }

    // Xóa thể loại
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}