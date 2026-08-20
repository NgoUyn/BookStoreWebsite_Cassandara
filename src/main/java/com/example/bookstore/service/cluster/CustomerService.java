package com.example.bookstore.service.cluster;

import com.example.bookstore.dto.ml.CustomerMLInput;
import com.example.bookstore.model.Customer;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.BookReviewRepository;
import com.example.bookstore.repository.CartRepository;
import com.example.bookstore.repository.CustomerRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.OrderReturnRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service quản lý entity Customer (ML features & kết quả phân tích).
 * Chịu trách nhiệm CRUD và tính toán features từ dữ liệu có sẵn.
 * Đồng bộ với features_config.json → required_raw_features (12 fields).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final BookReviewRepository bookReviewRepository;
    private final CartRepository cartRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final SupportTicketRepository supportTicketRepository;

    /**
     * Tìm Customer theo user ID.
     */
    public Optional<Customer> findByUserId(Long userId) {
        return customerRepository.findByUserId(userId);
    }

    /**
     * Kiểm tra đã có Customer record cho user này chưa.
     */
    public boolean existsByUserId(Long userId) {
        return customerRepository.existsByUserId(userId);
    }

    /**
     * Lấy tất cả Customer records.
     */
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    /**
     * Lưu hoặc cập nhật Customer.
     */
    @Transactional
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    /**
     * Xoá Customer record theo ID.
     */
    @Transactional
    public void deleteById(Long id) {
        customerRepository.deleteById(id);
    }

    /**
     * Chuẩn hoá riskLevel từ Python API (VD: "LOW" hoặc "HIGH")
     * để đồng bộ với giao diện frontend.
     */
    public String normalizeRiskLevel(String rawRiskLevel) {
        if (rawRiskLevel == null || rawRiskLevel.isBlank()) {
            return null;
        }
        String trimmed = rawRiskLevel.trim().toUpperCase();
        // Nếu đã là dạng thuần (LOW/HIGH) thì giữ nguyên
        if (trimmed.equals("LOW") || trimmed.equals("HIGH")) {
            return trimmed;
        }
        // Nếu có dạng "LOW (Tiếng Việt)" → lấy phần CODE
        if (trimmed.startsWith("LOW")) return "LOW";
        if (trimmed.startsWith("HIGH")) return "HIGH";
        // Fallback: giữ nguyên
        return trimmed;
    }

    /**
     * Map dữ liệu từ entity Customer sang DTO gửi lên ML API.
     * Đồng bộ với features_config.json → required_raw_features (12 fields).
     *
     * @param customer entity Customer từ database
     * @return CustomerMLInput DTO (12 raw features)
     */
    public CustomerMLInput toMlInput(Customer customer) {
        return CustomerMLInput.builder()
                // Thứ tự features phải khớp với docs/Cluster/features_config.json → required_raw_features (index 1-12)
                .accountAgeMonths(customer.getAccountAgeMonths())           // index 1
                .avgOrderValue(customer.getAvgOrderValue())                 // index 2
                .browsingFrequencyPerWeek(customer.getBrowsingFrequencyPerWeek()) // index 3
                .cartAbandonmentRate(customer.getCartAbandonmentRate())     // index 4
                .customerSupportTickets(customer.getCustomerSupportTickets()) // index 5
                .discountUsageRate(customer.getDiscountUsageRate())         // index 6
                .loyaltyMember(customer.getLoyaltyMember())                 // index 7
                .priceSensitivityIndex(customer.getPriceSensitivityIndex()) // index 8
                .productReviewScoreAvg(customer.getProductReviewScoreAvg()) // index 9
                .returnRate(customer.getReturnRate())                       // index 10
                .satisfactionScore(customer.getSatisfactionScore())         // index 11
                .totalOrders(customer.getTotalOrders())                     // index 12
                .build();
    }

    /**
     * Cập nhật kết quả ML vào entity Customer.
     * Tự động chuẩn hoá riskLevel để đồng bộ với giao diện frontend.
     *
     * @param customer         entity cần cập nhật
     * @param predictedLabel   nhãn dự đoán (0 = Stay, 1 = Churn)
     * @param churnProbability xác suất rời bỏ
     * @param riskLevel        mức độ rủi ro (LOW/HIGH)
     */
    public void updateMlResult(Customer customer, Integer predictedLabel,
                               Double churnProbability, String riskLevel) {
        customer.setPredictedLabel(predictedLabel);
        customer.setChurnProbability(churnProbability);
        customer.setRiskLevel(normalizeRiskLevel(riskLevel));
        customer.setLastAnalyzedAt(java.time.LocalDateTime.now());
    }

    /**
     * Tạo entity Customer mới từ User (với giá trị mặc định).
     * Dùng khi lần đầu phân tích khách hàng.
     */
    public Customer createDefault(User user) {
        Customer customer = Customer.builder()
                .user(user)
                .accountAgeMonths(0.0)
                .avgOrderValue(0.0)
                .totalOrders(0.0)
                .customerSupportTickets(0.0)
                .loyaltyMember(0.0) // 0.0 = No
                .browsingFrequencyPerWeek(0.0)
                .cartAbandonmentRate(0.0)
                .productReviewScoreAvg(0.0)
                .satisfactionScore(0.0)
                .priceSensitivityIndex(0.0)
                .discountUsageRate(0.0)
                .returnRate(0.0)
                .build();
        return customerRepository.save(customer);
    }

    /**
     * Tính toán các ML features từ dữ liệu thực tế của user (orders, reviews, cart, returns).
     * Dùng khi lần đầu phân tích hoặc khi cần refresh dữ liệu.
     * Đồng bộ với features_config.json → required_raw_features (12 fields).
     *
     * @param user User cần tính features
     * @return Customer entity đã được gán features (chưa save)
     */
    public Customer computeFeatures(User user) {
        // ====================================================================
        // 1. Order-based features (THẬT)
        // ====================================================================
        long totalOrders = orderRepository.countByBuyer(user);
        Double totalSpent = orderRepository.sumTotalAmountByBuyer(user);
        if (totalSpent == null) totalSpent = 0.0;
        Double avgOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        // ====================================================================
        // 2. Account age (THẬT từ User.createdAt)
        // ====================================================================
        double accountAgeMonths = 0.0;
        if (user.getCreatedAt() != null) {
            accountAgeMonths = ChronoUnit.MONTHS.between(user.getCreatedAt(), LocalDateTime.now());
        }

        // ====================================================================
        // 3. Review-based features (THẬT)
        // ====================================================================
        Double avgRating = bookReviewRepository.findAverageRatingByUser(user);
        if (avgRating == null) avgRating = 0.0;

        // ====================================================================
        // 4. Cart abandonment (THẬT)
        // ====================================================================
        double cartAbandonmentRate = 0.0;
        var cartOpt = cartRepository.findByBuyerId(user.getId());
        if (cartOpt.isPresent()) {
            int cartItemCount = cartOpt.get().getItems().size();
            if (totalOrders > 0) {
                cartAbandonmentRate = Math.min(1.0, (double) cartItemCount / totalOrders);
            } else if (cartItemCount > 0) {
                cartAbandonmentRate = 0.8;
            }
        }

        // ====================================================================
        // 5. Loyalty member (THẬT) — 0.0 = No, 1.0 = Yes
        // ====================================================================
        double loyaltyMember = (totalOrders >= 5 || totalSpent >= 1_000_000) ? 1.0 : 0.0;

        // ====================================================================
        // 6. Satisfaction score (THẬT từ rating)
        // ====================================================================
        double satisfactionScore = avgRating > 0 ? (avgRating / 5.0) * 100.0 : 0.0;

        // ====================================================================
        // 7. Product review score avg (THẬT)
        // ====================================================================
        double productReviewScoreAvg = avgRating;

        // ====================================================================
        // 8. Customer support tickets (THẬT từ bảng support_tickets)
        // ====================================================================
        long ticketCount = supportTicketRepository.countByUser(user);
        double customerSupportTickets = (double) ticketCount;

        // ====================================================================
        // 9. Browsing frequency per week (THẬT từ user_activity_log)
        // ====================================================================
        // Lưu ý: Cần inject UserActivityLogRepository. Tạm thời ước lượng từ orders + reviews.
        long reviewCount = bookReviewRepository.countByUser(user);
        double browsingFrequencyPerWeek = Math.min(7.0, (totalOrders + reviewCount) / 4.0);

        // ====================================================================
        // 10. Price sensitivity index (THẬT từ discount behavior)
        // ====================================================================
        double priceSensitivityIndex = 5.0; // mặc định trung bình
        if (totalOrders > 0) {
            Double totalDiscount = orderRepository.sumDiscountAmountByBuyer(user);
            if (totalDiscount == null) totalDiscount = 0.0;
            double discountRatio = totalSpent > 0 ? totalDiscount / totalSpent : 0.0;
            // discountRatio: 0-1, map sang 1-10
            priceSensitivityIndex = Math.min(10.0, Math.max(1.0, discountRatio * 20.0));
        }

        // ====================================================================
        // 11. Discount usage rate (THẬT từ Order)
        // ====================================================================
        double discountUsageRate = 0.0;
        if (totalOrders > 0) {
            long discountedOrders = orderRepository.countDiscountedOrdersByBuyer(user);
            discountUsageRate = (double) discountedOrders / totalOrders;
        }

        // ====================================================================
        // 12. Return rate (THẬT từ OrderReturn)
        // ====================================================================
        double returnRate = 0.0;
        Long returnedQuantity = orderReturnRepository.sumReturnedQuantityByUser(user);
        if (returnedQuantity == null) returnedQuantity = 0L;
        // Tính tổng số lượng sản phẩm đã mua từ sub_orders
        long totalOrderedItems = subOrderRepository.countByBuyer(user);
        if (totalOrderedItems > 0) {
            returnRate = Math.min(1.0, returnedQuantity.doubleValue() / totalOrderedItems);
        }

        // ====================================================================
        // Build Customer entity với features đã tính
        // ====================================================================
        Customer customer = Customer.builder()
                .user(user)
                .accountAgeMonths(accountAgeMonths)
                .avgOrderValue(avgOrderValue)
                .totalOrders((double) totalOrders)
                .customerSupportTickets(customerSupportTickets)
                .loyaltyMember(loyaltyMember)
                .browsingFrequencyPerWeek(browsingFrequencyPerWeek)
                .cartAbandonmentRate(cartAbandonmentRate)
                .productReviewScoreAvg(productReviewScoreAvg)
                .satisfactionScore(satisfactionScore)
                .priceSensitivityIndex(priceSensitivityIndex)
                .discountUsageRate(discountUsageRate)
                .returnRate(returnRate)
                .build();

        log.info("Computed ML features for user {}: totalOrders={}, totalSpent={}, avgOrderValue={}, " +
                        "avgRating={}, loyaltyMember={}, accountAgeMonths={}, priceSensitivityIndex={}, " +
                        "discountUsageRate={}, returnRate={}",
                user.getId(), totalOrders, totalSpent, avgOrderValue,
                avgRating, loyaltyMember, accountAgeMonths, priceSensitivityIndex,
                discountUsageRate, returnRate);

        return customer;
    }
}
