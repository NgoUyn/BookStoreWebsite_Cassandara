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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.model.Order;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
 
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerDashboardController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
   private final OrderRepository orderRepository;
    

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
    // 1. Xác định Seller đang đăng nhập
    Long sellerId = null;

    if (principal != null) {
        sellerId = principal.sellerId() != null
                ? principal.sellerId()
                : principal.userId();
    } else if (xUserId != null) {
        try {
            sellerId = Long.parseLong(xUserId);
        } catch (NumberFormatException e) {
            return Map.of("error", "Invalid user ID");
        }
    }

    if (sellerId == null) {
        return Map.of("error", "Unauthorized");
    }

    User seller = userRepository.findById(sellerId).orElse(null);

    if (seller == null) {
        return Map.of("error", "Seller not found");
    }

    List<Book> sellerBooks = bookRepository.findBySeller(seller);

    // 2. Lấy dữ liệu từ collection orders
    List<Order> orders = orderRepository.findAll();

    // Lọc các đơn hàng con thuộc Seller
    // Đồng thời giữ liên kết đến Order cha để lấy ngày và khách hàng.
    Map<SubOrder, Order> parentOrders = new IdentityHashMap<>();
    List<SubOrder> subOrders = new ArrayList<>();

    for (Order order : orders) {
        if (order == null || order.getSubOrders() == null) {
            continue;
        }

        for (SubOrder so : order.getSubOrders()) {
            if (so == null || !Objects.equals(so.getSellerId(), sellerId)) {
                continue;
            }

            subOrders.add(so);
            parentOrders.put(so, order);
        }
    }

    // 3. Thống kê tổng quan
    // Tạm tính doanh thu từ đơn COMPLETED.
    double totalRevenue = subOrders.stream()
            .filter(so -> so.getStatus() == OrderStatus.COMPLETED)
            .mapToDouble(so ->
                    so.getSubTotal() == null ? 0d : so.getSubTotal())
            .sum();

    long totalOrders = subOrders.size();

    long uniqueCustomers = subOrders.stream()
            .map(parentOrders::get)
            .filter(Objects::nonNull)
            .map(Order::getBuyerId)
            .filter(Objects::nonNull)
            .distinct()
            .count();

    long totalProducts = sellerBooks == null ? 0 : sellerBooks.size();

    Map<String, Object> summaryStats = new LinkedHashMap<>();
    summaryStats.put("totalRevenue", totalRevenue);
    summaryStats.put("totalOrders", totalOrders);
    summaryStats.put("uniqueCustomers", uniqueCustomers);
    summaryStats.put("totalProducts", totalProducts);

    // 4. Doanh thu theo ngày
    int days = "month".equalsIgnoreCase(period) ? 30 : 7;

    LocalDate today = LocalDate.now();

    Map<String, Double> revenueByDayMap = new LinkedHashMap<>();

    for (int i = days - 1; i >= 0; i--) {
        String dateKey = today.minusDays(i)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        revenueByDayMap.put(dateKey, 0d);
    }

    for (SubOrder so : subOrders) {
        if (so.getStatus() != OrderStatus.COMPLETED) {
            continue;
        }

        Order parent = parentOrders.get(so);

        if (parent == null || parent.getCreatedAt() == null) {
            continue;
        }

        LocalDate orderDate = parent.getCreatedAt().toLocalDate();

        String dateKey = orderDate.format(
                DateTimeFormatter.ISO_LOCAL_DATE);

        if (revenueByDayMap.containsKey(dateKey)) {
            revenueByDayMap.merge(
                    dateKey,
                    so.getSubTotal() == null ? 0d : so.getSubTotal(),
                    Double::sum
            );
        }
    }

    List<Map<String, Object>> revenueByDay =
            revenueByDayMap.entrySet().stream()
                    .map(e -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("date", e.getKey());
                        entry.put("revenue", e.getValue());
                        return entry;
                    })
                    .collect(Collectors.toList());

    // 5. Phân bố trạng thái đơn hàng
    Map<String, Long> orderStatusDistribution =
            subOrders.stream()
                    .collect(Collectors.groupingBy(
                            so -> so.getStatus() == null
                                    ? "UNKNOWN"
                                    : so.getStatus().toString(),
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

    for (OrderStatus status : OrderStatus.values()) {
        orderStatusDistribution.putIfAbsent(
                status.toString(), 0L);
    }

    // 6. Thống kê danh mục
    Map<String, Long> categoryDistribution = new LinkedHashMap<>();

    for (SubOrder so : subOrders) {
        if (so.getStatus() == OrderStatus.CANCELLED
        || (so.getStatus() != null
            && "RETURNED".equals(so.getStatus().name()))
        || so.getItems() == null) {
    continue;
}

        for (var item : so.getItems()) {
            if (item == null) {
                continue;
            }

            String catName =
                    item.getBook() != null
                    && item.getBook().getCategory() != null
                    && item.getBook().getCategory().getName() != null
                            ? item.getBook().getCategory().getName()
                            : "Chưa phân loại";

            long qty = item.getQuantity() == null
                    ? 0
                    : item.getQuantity();

            categoryDistribution.merge(
                    catName, qty, Long::sum);
        }
    }

    // 7. Tăng trưởng khách hàng
    Map<String, Map<String, Long>> customerGrowth =
            new LinkedHashMap<>();

    Map<Long, LocalDateTime> buyerFirstOrder =
            new LinkedHashMap<>();

    Map<Long, List<LocalDateTime>> buyerOrdersByMonth =
            new LinkedHashMap<>();

    for (SubOrder so : subOrders) {
        Order parent = parentOrders.get(so);

        if (parent == null
                || parent.getBuyerId() == null
                || parent.getCreatedAt() == null) {
            continue;
        }

        Long buyerId = parent.getBuyerId();
        LocalDateTime orderDate = parent.getCreatedAt();

        buyerOrdersByMonth
                .computeIfAbsent(buyerId, k -> new ArrayList<>())
                .add(orderDate);

        buyerFirstOrder.merge(
                buyerId,
                orderDate,
                (oldDate, newDate) ->
                        newDate.isBefore(oldDate) ? newDate : oldDate
        );
    }

    for (int i = 5; i >= 0; i--) {
        LocalDate monthStart = today.withDayOfMonth(1)
                .minusMonths(i);

        String monthKey = monthStart.format(
                DateTimeFormatter.ofPattern("MM/yyyy"));

        long newCustomers = 0;
        long returningCustomers = 0;

        for (var entry : buyerFirstOrder.entrySet()) {
            Long buyerId = entry.getKey();
            LocalDateTime firstDate = entry.getValue();

            boolean firstInMonth =
                    firstDate.getYear() == monthStart.getYear()
                    && firstDate.getMonth() == monthStart.getMonth();

            if (firstInMonth) {
                newCustomers++;
            } else if (firstDate.isBefore(monthStart.atStartOfDay())) {
                List<LocalDateTime> dates =
                        buyerOrdersByMonth.get(buyerId);

                if (dates != null && dates.stream().anyMatch(d ->
                        d.getYear() == monthStart.getYear()
                        && d.getMonth() == monthStart.getMonth())) {
                    returningCustomers++;
                }
            }
        }

        Map<String, Long> monthData = new LinkedHashMap<>();
        monthData.put("new", newCustomers);
        monthData.put("returning", returningCustomers);

        customerGrowth.put(monthKey, monthData);
    }

    // 8. Doanh thu theo tháng
    Map<String, Map<String, Object>> monthlyRevenue =
            new LinkedHashMap<>();

    for (int i = 11; i >= 0; i--) {
        LocalDate monthStart = today.withDayOfMonth(1)
                .minusMonths(i);

        String monthKey = monthStart.format(
                DateTimeFormatter.ofPattern("MM/yyyy"));

        double monthRevenue = 0;
        long monthOrders = 0;

        for (SubOrder so : subOrders) {
            if (so.getStatus() != OrderStatus.COMPLETED) {
                continue;
            }

            Order parent = parentOrders.get(so);

            if (parent == null || parent.getCreatedAt() == null) {
                continue;
            }

            LocalDateTime orderDate = parent.getCreatedAt();

            if (orderDate.getYear() == monthStart.getYear()
                    && orderDate.getMonth() == monthStart.getMonth()) {

                monthRevenue += so.getSubTotal() == null
                        ? 0d
                        : so.getSubTotal();

                monthOrders++;
            }
        }

        Map<String, Object> monthData = new LinkedHashMap<>();
        monthData.put("revenue", monthRevenue);
        monthData.put("orders", monthOrders);

        monthlyRevenue.put(monthKey, monthData);
    }

    // 9. Trả dữ liệu về frontend
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
       List<SubOrder> subOrders = orderRepository.findAll()
        .stream()
        .filter(order -> order != null && order.getSubOrders() != null)
        .flatMap(order -> order.getSubOrders().stream())
        .filter(so -> so != null
                && java.util.Objects.equals(so.getSellerId(), seller.getId()))
        .collect(java.util.stream.Collectors.toList());

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
