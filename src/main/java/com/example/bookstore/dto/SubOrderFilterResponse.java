package com.example.bookstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for filtered sub-orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderFilterResponse {
    private List<SubOrderSummaryResponse> subOrders;
    private Long totalCount;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;
}
