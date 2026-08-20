package com.example.bookstore.controller;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seller")
public class SellerDashboardController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubOrderRepository subOrderRepository;

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 📊 Seller Dashboard Stats - API tổng hợp cho Dashboard mới
     * GET /api/seller/dashboard-stats?period=week
     */
    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasAuthority('SELLER')")
    public Map<String, Object> sellerDashboardStats(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestParam(defaultValue = "week") String period
    ) {
        Long sellerId = null;
        if (principal != null) {
            sellerId = principal.sellerId() != null ? principal.sellerId() : principal.userId();
        } else if (xUserId != null) {
            sellerId = Long.parseLong(xUserId);
        }

        if (sellerId == null) {
            return Map.of("error", "Unauthorized");
        }

        User seller = userRepository.findById(sellerId).orElse(null);
        if (seller == null) return Map.of("error", "Seller not found");

        List<Book> sellerBooks = bookRepository.findBySeller(seller);
        List<SubOrder> subOrders = subOrderRepository.findBySeller(seller);

        // ==========================================
        // 1. Summary Stats
        // ==========================================
        double totalRevenue = subOrders.stream()
                .filter(so -> so.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(so -> so.getSubTotal() == null ? 0d : so.getSubTotal())
                .sum();

        long totalOrders = subOrders.stream()
                .filter(so -> so.getStatus() != OrderStatus.CANCELLED)
                .count();

        long uniqueCustomers = subOrders.stream()
                .filter(so -> so.getParentOrder() != null && so.getParentOrder().getBuyer() != null)
                .map(so -> so.getParentOrder().getBuyer().getId())
                .distinct()
                .count();

        long totalProducts = sellerBooks.size();

        Map<String, Object> summaryStats = new LinkedHashMap<>();
        summaryStats.put("totalRevenue", totalRevenue);
        summaryStats.put("totalOrders", totalOrders);
        summaryStats.put("uniqueCustomers", uniqueCustomers);
        summaryStats.put("totalProducts", totalProducts);

        // ==========================================
        // 2. Revenue By Day (for bar chart)
        // ==========================================
        int days = period.equalsIgnoreCase("month") ? 30 : 7;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(days);

        Map<String, Double> revenueByDayMap = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            String dateKey = now.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            revenueByDayMap.put(dateKey, 0d);
        }

        for (SubOrder so : subOrders) {
            if (so.getStatus() == OrderStatus.CANCELLED) continue;
            LocalDateTime orderDate = so.getParentOrder().getCreatedAt();
            if (orderDate != null && !orderDate.isBefore(since)) {
                String dateKey = orderDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                revenueByDayMap.merge(dateKey, so.getSubTotal() == null ? 0d : so.getSubTotal(), Double::sum);
            }
        }

        List<Map<String, Object>> revenueByDay = revenueByDayMap.entrySet().stream()
                .map(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("date", e.getKey());
                    entry.put("revenue", e.getValue());
                    return entry;
                })
                .collect(Collectors.toList());

        // ==========================================
        // 3. Order Status Distribution
        // ==========================================
        Map<String, Long> orderStatusDistribution = subOrders.stream()
                .collect(Collectors.groupingBy(
                        so -> so.getStatus().toString(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        for (OrderStatus status : OrderStatus.values()) {
            orderStatusDistribution.putIfAbsent(status.toString(), 0L);
        }

        // ==========================================
        // 4. Category Distribution
        // ==========================================
        Map<String, Long> categoryDistribution = new LinkedHashMap<>();
        for (SubOrder so : subOrders) {
            if (so.getStatus() == OrderStatus.CANCELLED) continue;
            if (so.getItems() == null) continue;
            for (var item : so.getItems()) {
                String catName = (item.getBook() != null && item.getBook().getCategory() != null)
                        ? item.getBook().getCategory().getName() : "Chua phan loai";
                long qty = item.getQuantity() != null ? item.getQuantity() : 0;
                categoryDistribution.merge(catName, qty, Long::sum);
            }
        }

        // ==========================================
        // 5. Customer Growth
        // ==========================================
        Map<String, Map<String, Long>> customerGrowth = new LinkedHashMap<>();

        Map<Long, LocalDateTime> buyerFirstOrder = new LinkedHashMap<>();
        Map<Long, List<LocalDateTime>> buyerOrdersByMonth = new LinkedHashMap<>();

        for (SubOrder so : subOrders) {
            if (so.getParentOrder() == null || so.getParentOrder().getBuyer() == null) continue;
            Long buyerId = so.getParentOrder().getBuyer().getId();
            LocalDateTime orderDate = so.getParentOrder().getCreatedAt();
            if (orderDate == null) continue;

            buyerOrdersByMonth.computeIfAbsent(buyerId, k -> new ArrayList<>()).add(orderDate);

            if (!buyerFirstOrder.containsKey(buyerId) || orderDate.isBefore(buyerFirstOrder.get(buyerId))) {
                buyerFirstOrder.put(buyerId, orderDate);
            }
        }

        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = LocalDate.from(now).withDayOfMonth(1).minusMonths(i);
            String monthKey = monthStart.format(DateTimeFormatter.ofPattern("MM/yyyy"));

            long newCustomers = 0;
            long returningCustomers = 0;

            for (var entry : buyerFirstOrder.entrySet()) {
                Long buyerId = entry.getKey();
                LocalDateTime firstOrderDate = entry.getValue();

                if (firstOrderDate.toLocalDate().getYear() == monthStart.getYear()
                        && firstOrderDate.toLocalDate().getMonth() == monthStart.getMonth()) {
                    newCustomers++;
                } else if (firstOrderDate.isBefore(monthStart.atStartOfDay())) {
                    List<LocalDateTime> buyerDates = buyerOrdersByMonth.get(buyerId);
                    if (buyerDates != null) {
                        boolean hadOrderThisMonth = buyerDates.stream().anyMatch(d ->
                                d.toLocalDate().getYear() == monthStart.getYear()
                                && d.toLocalDate().getMonth() == monthStart.getMonth());
                        if (hadOrderThisMonth) {
                            returningCustomers++;
                        }
                    }
                }
            }

            Map<String, Long> monthData = new LinkedHashMap<>();
            monthData.put("new", newCustomers);
            monthData.put("returning", returningCustomers);
            customerGrowth.put(monthKey, monthData);
        }

        // ==========================================
        // 6. Monthly Revenue
        // ==========================================
        Map<String, Map<String, Object>> monthlyRevenue = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDate monthStart = LocalDate.from(now).withDayOfMonth(1).minusMonths(i);
            String monthKey = monthStart.format(DateTimeFormatter.ofPattern("MM/yyyy"));

            double monthRevenue = 0;
            long monthOrders = 0;

            for (SubOrder so : subOrders) {
                if (so.getStatus() == OrderStatus.CANCELLED) continue;
                LocalDateTime orderDate = so.getParentOrder().getCreatedAt();
                if (orderDate != null
                        && orderDate.toLocalDate().getYear() == monthStart.getYear()
                        && orderDate.toLocalDate().getMonth() == monthStart.getMonth()) {
                    monthRevenue += so.getSubTotal() == null ? 0d : so.getSubTotal();
                    monthOrders++;
                }
            }

            Map<String, Object> monthData = new LinkedHashMap<>();
            monthData.put("revenue", monthRevenue);
            monthData.put("orders", monthOrders);
            monthlyRevenue.put(monthKey, monthData);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summaryStats", summaryStats);
        response.put("revenueByDay", revenueByDay);
        response.put("orderStatusDistribution", orderStatusDistribution);
        response.put("categoryDistribution", categoryDistribution);
        response.put("customerGrowth", customerGrowth);
        response.put("monthlyRevenue", monthlyRevenue);

        return response;
    }

    /**
     * 📊 Seller Analytics - API cho trang Analytics
     * GET /api/seller/analytics
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('SELLER')")
    public Map<String, Object> sellerAnalytics(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId
    ) {
        Long sellerId = null;
        if (principal != null) {
            sellerId = principal.sellerId() != null ? principal.sellerId() : principal.userId();
        } else if (xUserId != null) {
            sellerId = Long.parseLong(xUserId);
        }

        if (sellerId == null) {
            return Map.of("error", "Unauthorized");
        }

        User seller = userRepository.findById(sellerId).orElse(null);
        if (seller == null) return Map.of("error", "Seller not found");

        List<Book> sellerBooks = bookRepository.findBySeller(seller);
        List<SubOrder> subOrders = subOrderRepository.findBySeller(seller);

        double estimatedRevenue = subOrders.stream()
                .filter(so -> so.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(so -> so.getSubTotal() == null ? 0d : so.getSubTotal())
                .sum();

        double averagePrice = sellerBooks.stream()
                .mapToDouble(b -> b.getPrice() == null ? 0d : b.getPrice())
                .average()
                .orElse(0d);

        long lowStock = sellerBooks.stream().filter(b -> {
            Integer qty = b.getStockQuantity();
            return qty != null && qty < 10;
        }).count();

        Map<String, Long> categoryCounts = sellerBooks.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getCategory() != null && safe(b.getCategory().getName()).length() > 0 ? b.getCategory().getName() : "Chua phan loai",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Double> categoryRevenue = new LinkedHashMap<>();
        for (SubOrder so : subOrders) {
            if (so.getStatus() == OrderStatus.CANCELLED) continue;
            if (so.getItems() == null) continue;
            for (var item : so.getItems()) {
                String catName = (item.getBook() != null && item.getBook().getCategory() != null)
                        ? item.getBook().getCategory().getName() : "Chua phan loai";
                double itemValue = (item.getUnitPrice() != null ? item.getUnitPrice() : 0d) * (item.getQuantity() != null ? item.getQuantity() : 0);
                categoryRevenue.put(catName, categoryRevenue.getOrDefault(catName, 0d) + itemValue);
            }
        }

        Map<String, Long> orderStatusCounts = subOrders.stream()
                .collect(Collectors.groupingBy(
                        so -> so.getStatus().toString(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("estimatedRevenue", estimatedRevenue);
        response.put("averagePrice", averagePrice);
        response.put("bookCount", sellerBooks.size());
        response.put("lowStock", lowStock);
        response.put("categoryCounts", categoryCounts);
        response.put("categoryRevenue", categoryRevenue);
        response.put("orderStatusCounts", orderStatusCounts);

        List<Map<String, Object>> recentOrders = subOrders.stream()
                .sorted(Comparator.comparing(SubOrder::getId).reversed())
                .limit(10)
                .map(so -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", so.getId());
                    map.put("customer", so.getParentOrder() != null && so.getParentOrder().getBuyer() != null
                            ? so.getParentOrder().getBuyer().getUsername() : "N/A");
                    String itemSummary = (so.getItems() != null && !so.getItems().isEmpty())
                            ? so.getItems().get(0).getBook().getTitle() + (so.getItems().size() > 1 ? "..." : "")
                            : "N/A";
                    map.put("item", itemSummary);
                    map.put("value", so.getSubTotal());
                    map.put("status", so.getStatus().toString());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("recentOrders", recentOrders);

        return response;
    }
}
