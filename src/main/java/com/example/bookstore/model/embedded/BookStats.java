package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thong ke ban hang cua sach - EMBED trong {@code books.stats{}}.
 * Duoc cap nhat bang {@code $inc} (khi ban) hoac tinh lai bang {@code $merge}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookStats {

    private Integer soldCount;
    private Integer viewCount;
    private Integer wishlistCount;
    private Double revenue;
}
