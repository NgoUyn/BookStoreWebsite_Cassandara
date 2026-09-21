package com.example.bookstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dong san pham cua 1 subOrder - NHUNG trong {@code orders.subOrders[].items[]}
 * (SQL Server truoc day la bang {@code order_items}).
 *
 * <p>Luu SNAPSHOT (title/imageUrl/unitPrice) tai thoi diem mua de hoa don
 * khong bi thay doi khi seller sua gia/ten sach; {@code bookId} van giu de
 * thong ke/tra hang.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    /** id dong hang (counters "order_items"). */
    @org.springframework.data.mongodb.core.mapping.Field("itemId")
    private Long id;

    private Long subOrderId;

    private Long bookId;

    // ---------- snapshot tai thoi diem mua ----------
    private String title;
    private String imageUrl;
    private String sellerName;

    private Double unitPrice;

    private Integer quantity;

    /** So luong da tra (dung tinh return_rate cho ML). */
    @Builder.Default
    private Integer returnedQuantity = 0;

    @JsonIgnore
    public Double getLineTotal() {
        double unit = unitPrice == null ? 0.0 : unitPrice;
        int qty = quantity == null ? 0 : quantity;
        return unit * qty;
    }

    /** Cap nhat snapshot tu Book khi tao don. */
    public void applyBookSnapshot(Book book) {
        if (book == null) {
            return;
        }
        this.bookId = book.getId();
        this.title = book.getTitle();
        this.imageUrl = book.getImageUrl();
        if (book.getSeller() != null) {
            this.sellerName = book.getSeller().getShopName() != null
                    ? book.getSeller().getShopName()
                    : book.getSeller().getUsername();
        }
    }

    /**
     * TUONG THICH (compat): code cu goi {@code item.getBook().getTitle()}.
     * Tra ve Book "nhe" dung tu snapshot - khong doc lai collection books
     * (hoa don phai giu gia/ten tai thoi diem mua).
     */
    @JsonIgnore
    public Book getBook() {
        return Book.builder()
                .id(bookId)
                .title(title)
                .imageUrl(imageUrl)
                .build();
    }

    public void setBook(Book book) {
        applyBookSnapshot(book);
    }
}
