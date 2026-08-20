package com.example.bookstore.dto;

import com.example.bookstore.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for filtering orders
 * Supports filtering by status, date range, price range, and seller/buyer name
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilterRequest {
    
    // Status filter
    private OrderStatus status;
    
    // Date range filters
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    
    // Price range filters
    private Double minPrice;
    private Double maxPrice;
    
    // Seller/Buyer name filter (for search)
    private String sellerName;
    private String buyerName;
    
    // Pagination
    private Integer page;
    private Integer pageSize;
    
    // Sorting
    private String sortBy; // createdAt, totalAmount, status
    private String sortDirection; // ASC, DESC
}
