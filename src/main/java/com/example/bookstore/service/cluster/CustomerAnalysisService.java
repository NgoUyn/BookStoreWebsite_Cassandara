package com.example.bookstore.service.cluster;

import com.example.bookstore.dto.ml.CustomerMLInput;
import com.example.bookstore.dto.ml.PredictionResult;
import com.example.bookstore.model.Customer;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrator: phối hợp lấy features → gọi ML API → lưu kết quả.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAnalysisService {

    private final CustomerService customerService;
    private final MlApiService mlApiService;
    private final UserRepository userRepository;

    /**
     * Phân tích một khách hàng cụ thể theo user ID.
     * Nếu chưa có Customer record, tự động tính features từ dữ liệu thực tế (orders, reviews).
     * Nếu đã có, vẫn refresh features trước khi gửi lên ML API.
     *
     * @param userId ID của user cần phân tích
     * @return Customer đã được cập nhật kết quả ML
     */
    @Transactional
    public Customer analyzeCustomer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 1. Tính toán features từ dữ liệu thực tế (orders, reviews, cart)
        Customer customer = customerService.findByUserId(userId)
                .orElse(null);

        if (customer == null) {
            // Chưa có record → tạo mới với features từ dữ liệu thật
            customer = customerService.computeFeatures(user);
            log.info("Created new Customer record for user {} with computed features", userId);
        } else {
            // Đã có record → refresh features từ dữ liệu mới nhất
            Customer freshFeatures = customerService.computeFeatures(user);
            customer.setAccountAgeMonths(freshFeatures.getAccountAgeMonths());
            customer.setAvgOrderValue(freshFeatures.getAvgOrderValue());
            customer.setTotalOrders(freshFeatures.getTotalOrders());
            customer.setCustomerSupportTickets(freshFeatures.getCustomerSupportTickets());
            customer.setLoyaltyMember(freshFeatures.getLoyaltyMember());
            customer.setBrowsingFrequencyPerWeek(freshFeatures.getBrowsingFrequencyPerWeek());
            customer.setCartAbandonmentRate(freshFeatures.getCartAbandonmentRate());
            customer.setProductReviewScoreAvg(freshFeatures.getProductReviewScoreAvg());
            customer.setSatisfactionScore(freshFeatures.getSatisfactionScore());
            customer.setPriceSensitivityIndex(freshFeatures.getPriceSensitivityIndex());
            customer.setDiscountUsageRate(freshFeatures.getDiscountUsageRate());
            customer.setReturnRate(freshFeatures.getReturnRate());
            log.info("Refreshed ML features for existing Customer record of user {}", userId);
        }

        // 2. Map sang DTO gửi lên ML API
        CustomerMLInput input = customerService.toMlInput(customer);

        // ================================================================
        // DEBUG: In ra toàn bộ features trước khi gửi lên Python API
        // ================================================================
        log.info("========== 🧠 DEBUG: Features gửi lên ML API cho userId={} ==========", userId);
        log.info("accountAgeMonths={}", input.getAccountAgeMonths());
        log.info("avgOrderValue={}", input.getAvgOrderValue());
        log.info("totalOrders={}", input.getTotalOrders());
        log.info("customerSupportTickets={}", input.getCustomerSupportTickets());
        log.info("loyaltyMember={}", input.getLoyaltyMember());
        log.info("browsingFrequencyPerWeek={}", input.getBrowsingFrequencyPerWeek());
        log.info("cartAbandonmentRate={}", input.getCartAbandonmentRate());
        log.info("productReviewScoreAvg={}", input.getProductReviewScoreAvg());
        log.info("satisfactionScore={}", input.getSatisfactionScore());
        log.info("priceSensitivityIndex={}", input.getPriceSensitivityIndex());
        log.info("discountUsageRate={}", input.getDiscountUsageRate());
        log.info("returnRate={}", input.getReturnRate());
        log.info("================================================================");

        // 3. Gọi ML API
        PredictionResult result = mlApiService.predictCustomer(input);

        // 4. Cập nhật kết quả vào entity
        // Python API trả về: predicted_label (0/1), churn_probability, risk_level (LOW/HIGH)
        customerService.updateMlResult(
                customer,
                result.getPredictedLabel(),   // predicted_label từ Python
                result.getChurnProbability(),
                result.getRiskLevel()
        );

        // 5. Lưu lại
        return customerService.save(customer);
    }

    /**
     * Phân tích tất cả khách hàng (dành cho Admin).
     * Refresh features từ dữ liệu thực tế trước khi gửi lên ML API.
     */
    @Transactional
    public int analyzeAllCustomers() {
        var customers = customerService.findAll();
        int count = 0;
        for (Customer customer : customers) {
            try {
                // Refresh features từ dữ liệu thực tế
                User user = customer.getUser();
                if (user != null) {
                    Customer freshFeatures = customerService.computeFeatures(user);
                    customer.setAccountAgeMonths(freshFeatures.getAccountAgeMonths());
                    customer.setAvgOrderValue(freshFeatures.getAvgOrderValue());
                    customer.setTotalOrders(freshFeatures.getTotalOrders());
                    customer.setCustomerSupportTickets(freshFeatures.getCustomerSupportTickets());
                    customer.setLoyaltyMember(freshFeatures.getLoyaltyMember());
                    customer.setBrowsingFrequencyPerWeek(freshFeatures.getBrowsingFrequencyPerWeek());
                    customer.setCartAbandonmentRate(freshFeatures.getCartAbandonmentRate());
                    customer.setProductReviewScoreAvg(freshFeatures.getProductReviewScoreAvg());
                    customer.setSatisfactionScore(freshFeatures.getSatisfactionScore());
                    customer.setPriceSensitivityIndex(freshFeatures.getPriceSensitivityIndex());
                    customer.setDiscountUsageRate(freshFeatures.getDiscountUsageRate());
                    customer.setReturnRate(freshFeatures.getReturnRate());
                }

                CustomerMLInput input = customerService.toMlInput(customer);
                PredictionResult result = mlApiService.predictCustomer(input);
                customerService.updateMlResult(
                        customer,
                        result.getPredictedLabel(),
                        result.getChurnProbability(),
                        result.getRiskLevel()
                );
                customerService.save(customer);
                count++;
            } catch (Exception e) {
                log.error("Failed to analyze customer {}: {}", customer.getId(), e.getMessage());
            }
        }
        log.info("Analyzed {} / {} customers", count, customers.size());
        return count;
    }
}
