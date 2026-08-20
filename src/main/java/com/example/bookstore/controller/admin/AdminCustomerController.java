package com.example.bookstore.controller.admin;

import com.example.bookstore.model.Customer;
import com.example.bookstore.service.cluster.CustomerAnalysisService;
import com.example.bookstore.service.cluster.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin controller: quản lý & xem phân tích khách hàng toàn hệ thống.
 */
@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;
    private final CustomerAnalysisService customerAnalysisService;

    /**
     * Lấy danh sách tất cả khách hàng đã phân tích.
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(customerService.findAll());
    }

    /**
     * Xem chi tiết phân tích của một khách hàng.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Customer> getCustomerAnalysis(@PathVariable Long userId) {
        return customerService.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Phân tích (hoặc phân tích lại) một khách hàng cụ thể.
     */
    @PostMapping("/{userId}/analyze")
    public ResponseEntity<Customer> analyzeCustomer(@PathVariable Long userId) {
        Customer result = customerAnalysisService.analyzeCustomer(userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Phân tích tất cả khách hàng đã có trong hệ thống.
     */
    @PostMapping("/analyze-all")
    public ResponseEntity<Map<String, Object>> analyzeAllCustomers() {
        int count = customerAnalysisService.analyzeAllCustomers();
        return ResponseEntity.ok(Map.of(
                "message", "Analysis completed",
                "analyzedCount", count
        ));
    }
}
