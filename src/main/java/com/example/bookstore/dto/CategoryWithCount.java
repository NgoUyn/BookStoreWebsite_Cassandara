package com.example.bookstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin danh mục kèm số lượng sách đã duyệt.
 * Dùng cho sidebar lọc danh mục ở trang shop.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryWithCount {
    private Long id;
    private String name;
    private long count;
}
