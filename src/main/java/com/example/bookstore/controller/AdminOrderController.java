package com.example.bookstore.controller;

import com.example.bookstore.dto.OrderDetailResponse;
import com.example.bookstore.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Lấy tất cả đơn hàng (có phân trang và filter)
     */
    @GetMapping()
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        try {
            LocalDate from = (dateFrom != null && !dateFrom.isEmpty()) ? LocalDate.parse(dateFrom) : null;
            LocalDate to = (dateTo != null && !dateTo.isEmpty()) ? LocalDate.parse(dateTo) : null;

            Page<?> orders = orderService.getOrdersWithFilters(page, size, q, status, from, to);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    /**
     * Lấy chi tiết đơn hàng
     */
    @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getOrderDetails(
            @PathVariable Long id
    ) {
        try {
            OrderDetailResponse detail = orderService.getOrderDetailsForAdmin(id);
            if (detail == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Không tìm thấy đơn hàng");
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
