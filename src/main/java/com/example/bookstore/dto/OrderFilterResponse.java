package com.example.bookstore.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Response DTO for filtered orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilterResponse {
    private List<OrderSummaryResponse> orders;
    private Long totalCount;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;
}
