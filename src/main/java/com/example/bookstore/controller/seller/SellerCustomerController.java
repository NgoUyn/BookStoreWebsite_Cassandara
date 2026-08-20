package com.example.bookstore.controller.seller;

import com.example.bookstore.model.Customer;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.cluster.CustomerAnalysisService;
import com.example.bookstore.service.cluster.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seller controller: xem phân tích khách hàng đã mua hàng từ shop của seller.
 */
@Slf4j
@RestController
@RequestMapping("/api/seller/customers")
@PreAuthorize("hasAuthority('SELLER')")
@RequiredArgsConstructor
public class SellerCustomerController {

    private final CustomerService customerService;
    private final CustomerAnalysisService customerAnalysisService;
    private final SubOrderRepository subOrderRepository;
    private final UserRepository userRepository;

    /**
     * Lấy danh sách khách hàng đã mua hàng từ shop của seller hiện tại,
     * kèm thông tin phân tích ML (nếu có).
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getMyCustomers(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        try {
            Long sellerId = resolveSellerId(principal);
            log.info("DEBUG: SellerCustomerController.getMyCustomers() - sellerId={}", sellerId);
            if (sellerId == null) {
                log.warn("DEBUG: sellerId is null, returning 400");
                return ResponseEntity.badRequest().build();
            }

            User seller = userRepository.findById(sellerId).orElse(null);
            if (seller == null) {
                log.warn("DEBUG: Seller not found for sellerId={}, returning 400", sellerId);
                return ResponseEntity.badRequest().build();
            }

            // Lấy tất cả buyer đã từng mua hàng từ shop này
            // Sử dụng query đơn giản hơn, không JOIN FETCH items để tránh Cartesian product
            List<Long> buyerIds = subOrderRepository.findDistinctBuyerIdsBySeller(seller);
            log.info("DEBUG: Found {} unique buyerIds for sellerId={}", buyerIds.size(), sellerId);

            // Lấy thông tin phân tích ML cho từng buyer
            List<Map<String, Object>> result = buyerIds.stream()
                    .map(buyerId -> {
                        Map<String, Object> entry = new java.util.LinkedHashMap<>();
                        User buyer = userRepository.findById(buyerId).orElse(null);
                        entry.put("userId", buyerId);
                        entry.put("username", buyer != null ? buyer.getUsername() : "N/A");
                        entry.put("email", buyer != null ? buyer.getEmail() : "N/A");

                        customerService.findByUserId(buyerId).ifPresentOrElse(
                                customer -> {
                                    entry.put("customerId", customer.getId());
                                    entry.put("predictedLabel", customer.getPredictedLabel());
                                    entry.put("churnProbability", customer.getChurnProbability());
                                    entry.put("riskLevel", customer.getRiskLevel());
                                    entry.put("lastAnalyzedAt", customer.getLastAnalyzedAt());
                                },
                                () -> {
                                    entry.put("customerId", null);
                                    entry.put("predictedLabel", null);
                                    entry.put("churnProbability", null);
                                    entry.put("riskLevel", "Not analyzed");
                                    entry.put("lastAnalyzedAt", null);
                                }
                        );
                        return entry;
                    })
                    .collect(Collectors.toList());

            log.info("DEBUG: Returning {} customers for sellerId={}", result.size(), sellerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("========== DEBUG: LỖI 500 TRONG GET SELLER CUSTOMERS ==========");
            log.error("sellerId={}", resolveSellerId(principal));
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Exception message: {}", e.getMessage());
            log.error("Full stacktrace:", e);
            log.error("================================================================");
            throw e; // Re-throw để Spring xử lý và trả về 500
        }
    }

    /**
     * Phân tích một khách hàng đã mua hàng từ shop của seller.
     */
    @PostMapping("/{buyerId}/analyze")
    @Transactional(readOnly = true)
    public ResponseEntity<Customer> analyzeCustomer(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @PathVariable Long buyerId
    ) {
        Long sellerId = resolveSellerId(principal, xUserId);
        if (sellerId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Kiểm tra buyer này đã từng mua hàng từ seller chưa
        User seller = userRepository.findById(sellerId).orElse(null);
        if (seller == null) {
            return ResponseEntity.badRequest().build();
        }

        boolean hasBoughtFromSeller = subOrderRepository.findDistinctBuyerIdsBySeller(seller)
                .stream()
                .anyMatch(id -> id.equals(buyerId));

        if (!hasBoughtFromSeller) {
            return ResponseEntity.status(403).build();
        }

        Customer result = customerAnalysisService.analyzeCustomer(buyerId);
        return ResponseEntity.ok(result);
    }

    private Long resolveSellerId(JwtAuthenticatedPrincipal principal) {
        if (principal != null) {
            return principal.sellerId() != null ? principal.sellerId() : principal.userId();
        }
        return null;
    }

    private Long resolveSellerId(JwtAuthenticatedPrincipal principal, String xUserId) {
        if (principal != null) {
            return principal.sellerId() != null ? principal.sellerId() : principal.userId();
        } else if (xUserId != null) {
            return Long.parseLong(xUserId);
        }
        return null;
    }
}
