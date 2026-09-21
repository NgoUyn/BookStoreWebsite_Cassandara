package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGGREGATE ROOT #3 - collection {@code carts}.
 *
 * <p>SQL Server co 2 bang {@code carts} + {@code cart_items} (JOIN moi lan doc).
 * MongoDB: 1 document / buyer, {@code items[]} EMBED - moi thao tac them/xoa/sua
 * gio hang la 1 update ATOMIC (khong can transaction).</p>
 */
@Document(collection = "carts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private Long buyerId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /** Tong tam tinh - cap nhat cung luc voi items (denormalized). */
    private CartTotals totals;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;

    /** Tinh lai tong tu danh sach item. */
    public void recalculateTotals() {
        int totalItems = 0;
        double subtotal = 0.0;
        if (items != null) {
            for (CartItem item : items) {
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                double unit = item.getUnitPrice() == null ? 0.0 : item.getUnitPrice();
                totalItems += qty;
                subtotal += unit * qty;
            }
        }
        this.totals = CartTotals.builder()
                .itemCount(totalItems)
                .lineCount(items == null ? 0 : items.size())
                .subtotal(subtotal)
                .build();
    }

    /** Tien ich cho tang service (thay cho quan he JPA 2 chieu). */
    public CartItem findItemByBookId(Long bookId) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .filter(i -> i.getBookId() != null && i.getBookId().equals(bookId))
                .findFirst()
                .orElse(null);
    }

    public CartItem findItemById(Long itemId) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .filter(i -> i.getId() != null && i.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }
}
