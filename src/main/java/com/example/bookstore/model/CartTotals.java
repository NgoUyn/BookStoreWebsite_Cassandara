package com.example.bookstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tong hop cua gio hang - EMBED trong {@code carts.totals{}}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartTotals {

    private Integer itemCount;
    private Integer lineCount;
    private Double subtotal;
}
