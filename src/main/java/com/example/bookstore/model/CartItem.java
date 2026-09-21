package com.example.bookstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dong san pham trong gio - NHUNG trong {@code carts.items[]}.
 *
 * <p>Luu snapshot thong tin hien thi (title/unitPrice/imageUrl/sellerName) de
 * trang gio hang khong phai doc collection books; ton kho van duoc kiem tra
 * "live" bang bookId truoc khi dat hang.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    /** id trong pham vi 1 gio hang (khong phai id toan cuc). */
    @org.springframework.data.mongodb.core.mapping.Field("itemId")
    private Long id;

    private Long bookId;
    private Long sellerId;
    private String sellerName;
    private String title;
    private String author;
    private Double unitPrice;
    private String imageUrl;
    private Integer quantity;
    private LocalDateTime addedAt;

    /** Cap nhat lai snapshot hien thi tu Book (khi gia/tieu de thay doi). */
    public void applyBookSnapshot(Book book) {
        if (book == null) {
            return;
        }
        this.bookId = book.getId();
        this.title = book.getTitle();
        this.author = book.getAuthor();
        this.unitPrice = book.getEffectivePrice();
        this.imageUrl = book.getImageUrl();
        if (book.getSeller() != null) {
            this.sellerId = book.getSellerId();
            this.sellerName = book.getSeller().getShopName() != null
                    ? book.getSeller().getShopName()
                    : book.getSeller().getUsername();
        }
    }
}
