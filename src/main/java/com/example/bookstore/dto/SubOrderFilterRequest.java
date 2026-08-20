package com.example.bookstore.dto;

import com.example.bookstore.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for filtering sub-orders (seller view)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderFilterRequest {
    
    // Status filter
    private OrderStatus status;
    
    // Date range filters
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    
    // Price range filters
    private Double minPrice;
    private Double maxPrice;
    
    // Buyer name filter (for search)
    private String buyerName;
    
    // Pagination
    private Integer page;
    private Integer pageSize;
    
    // Sorting
    private String sortBy; // createdAt, subTotal, status, id
    private String sortDirection; // ASC, DESC
}
