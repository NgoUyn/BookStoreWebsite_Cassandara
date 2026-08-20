package com.example.bookstore.controller;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.Order;
import com.example.bookstore.model.SubOrder;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/panel")
public class PanelController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SellerShopRepository sellerShopRepository;

    @Autowired
    private SubOrderRepository subOrderRepository;

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(String source, String keyword) {
        if (safe(keyword).isEmpty()) return true;
        return lower(source).contains(lower(keyword));
    }

    private static String stockBucket(Integer stockQuantity) {
        int stock = stockQuantity == null ? 0 : stockQuantity;
        if (stock < 10) return "low";
        if (stock < 50) return "normal";
        return "high";
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<Book> books = bookRepository.findAll();
        List<Category> categories = categoryRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        // GMV: Sum of all orders totalAmount
        double gmv = orders.stream()
                .mapToDouble(o -> o.getTotalAmount() == null ? 0d : o.getTotalAmount())
                .sum();

        Map<String, Long> categoryStats = books.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getCategory() != null && safe(b.getCategory().getName()).length() > 0 ? b.getCategory().getName() : "Chua phan loai",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> stockBuckets = books.stream()
                .collect(Collectors.groupingBy(
                        b -> stockBucket(b.getStockQuantity()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("gmv", gmv);
        response.put("books", books.size());
        response.put("categories", categories.size());
        response.put("shops", sellerShopRepository.count());
        response.put("categoryStats", categoryStats);
        response.put("stockBuckets", stockBuckets);

        // Add latest shops
        List<Map<String, Object>> latestShops = sellerShopRepository.findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream()
                .map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("shopName", s.getShopName());
                    map.put("owner", s.getSeller() != null ? s.getSeller().getUsername() : "N/A");
                    map.put("joined", "2026-03-01"); // TODO: Add createdAt to SellerShop
                    map.put("status", "Active");
                    return map;
                })
                .collect(Collectors.toList());
        response.put("latestShops", latestShops);

        return response;
    }

    @GetMapping("/books")
    public List<Map<String, Object>> books(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "all") String stock
    ) {
        return bookRepository.findAll().stream()
                .filter(b -> containsIgnoreCase(b.getTitle(), q) || containsIgnoreCase(b.getAuthor(), q))
                .filter(b -> safe(category).isEmpty() || "all".equalsIgnoreCase(category)
                        || (b.getCategory() != null && category.equalsIgnoreCase(safe(b.getCategory().getName()))))
                .filter(b -> {
                    String bucket = stockBucket(b.getStockQuantity());
                    return "all".equalsIgnoreCase(stock) || bucket.equalsIgnoreCase(stock);
                })
                .sorted(Comparator.comparing(Book::getId))
                .map(b -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", b.getId());
                    row.put("title", b.getTitle());
                    row.put("author", b.getAuthor());
                    row.put("price", b.getPrice());
                    row.put("stock", b.getStockQuantity());
                    row.put("stockBucket", stockBucket(b.getStockQuantity()));
                    row.put("category", b.getCategory() != null ? b.getCategory().getName() : "Chua phan loai");
                    return row;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String role,
            @RequestParam(defaultValue = "all") String status
    ) {
        return userRepository.findAll().stream()
                .filter(u -> containsIgnoreCase(u.getUsername(), q))
                .filter(u -> "all".equalsIgnoreCase(role) || u.getRole().toString().equalsIgnoreCase(role))
                .filter(u -> "all".equalsIgnoreCase(status) || (status.equalsIgnoreCase("Active") && u.isActive()) || (status.equalsIgnoreCase("Inactive") && !u.isActive()))
                .map(u -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", u.getUsername());
                    row.put("email", u.getUsername() + "@bookom.vn");
                    row.put("role", u.getRole().toString());
                    row.put("status", u.isActive() ? "Active" : "Inactive");
                    row.put("joined", "2026-02-01"); // TODO: Add createdAt to User
                    return row;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/shops")
    public List<Map<String, Object>> shops(@RequestParam(defaultValue = "") String q) {
        return sellerShopRepository.findAll().stream()
                .filter(s -> containsIgnoreCase(s.getShopName(), q)
                        || containsIgnoreCase(s.getSlug(), q))
                .map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("shopName", s.getShopName());
                    map.put("owner", s.getSeller() != null ? s.getSeller().getUsername() : "N/A");
                    map.put("legal", "N/A");
                    map.put("products", bookRepository.findBySeller(s.getSeller()).size());
                    map.put("joined", "2026-03-01");
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/seller/analytics")
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

        // Category Revenue calculation
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

        // Add recent orders
        List<Map<String, Object>> recentOrders = subOrders.stream()
                .sorted(Comparator.comparing(SubOrder::getId).reversed())
                .limit(10)
                .map(so -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", so.getId());
                    map.put("customer", so.getParentOrder() != null && so.getParentOrder().getBuyer() != null 
                            ? so.getParentOrder().getBuyer().getUsername() : "N/A");
                    // Get a summary of items
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

        /**
         * Lightweight dashboard summary for admin (orders counts, revenue, new users, low-stock)
         */
        @GetMapping("/dashboard")
        public Map<String, Object> dashboardSummary() {
                Map<String, Object> resp = new LinkedHashMap<>();

                List<Order> orders = orderRepository.findAll();
                List<User> users = userRepository.findAll();
                List<Book> books = bookRepository.findAll();

                // total orders
                resp.put("ordersCount", orders.size());

                // revenue (sum of totalAmount)
                double revenue = orders.stream()
                                .mapToDouble(o -> o.getTotalAmount() == null ? 0d : o.getTotalAmount())
                                .sum();
                resp.put("revenue", revenue);

                        // new users (best-effort): if User has createdAt field, count those; otherwise return total users
                        long newUsers;
                        try {
                                newUsers = users.stream().filter(u -> {
                                        try {
                                                java.lang.reflect.Field f = u.getClass().getDeclaredField("createdAt");
                                                f.setAccessible(true);
                                                Object val = f.get(u);
                                                if (val instanceof java.time.temporal.TemporalAccessor) return true; // best-effort
                                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
                                        return false;
                                }).count();
                                if (newUsers == 0) newUsers = users.size();
                        } catch (Exception ex) {
                                newUsers = users.size();
                        }
                resp.put("newUsers", newUsers);

                // low stock books (<20)
                        long lowStock = books.stream().filter(b -> {
                                Integer qty = b.getStockQuantity();
                                return qty != null && qty < 20;
                        }).count();
                resp.put("lowStock", lowStock);

                return resp;
        }

    /**
     * 🆕 NEW: Lấy danh sách người dùng chi tiết với trạng thái active/lock
     * GET /api/panel/users-detailed?q=&role=all&status=all
     */
    @GetMapping("/users-detailed")
    public List<Map<String, Object>> getUsersDetailed(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        return userRepository.findAll().stream()
                .filter(u -> q == null || q.isEmpty() || 
                        lower(u.getUsername()).contains(lower(q)))
                .filter(u -> role == null || role.equals("all") || 
                        u.getRole().toString().equalsIgnoreCase(role))
                .filter(u -> status == null || status.equals("all") ||
                        (status.equals("Active") && u.isActive()) ||
                        (status.equals("Inactive") && !u.isActive()))
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", u.getId());
                    map.put("name", u.getUsername());
                    map.put("email", u.getUsername() + "@bookom.vn");
                    map.put("role", u.getRole().toString());
                    map.put("status", u.isActive() ? "Active" : "Inactive");
                    map.put("joined", "2026-02-01");
                    map.put("action", u.isActive() ? "lock" : "unlock");
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy tất cả sách (với filter trạng thái duyệt và hoạt động)
     */
    @GetMapping("/books-all")
    public Page<Map<String, Object>> getAllBooksForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String active
    ) {
        Pageable pageable = PageRequest.of(page, size);
        List<Book> books = bookRepository.findAll();

        List<Map<String, Object>> filtered = books.stream()
                .filter(b -> q == null || q.isEmpty() ||
                        lower(b.getTitle()).contains(lower(q)) ||
                        lower(b.getAuthor()).contains(lower(q)))
                .filter(b -> approvalStatus == null || approvalStatus.equals("all") ||
                        b.getApprovalStatus().toString().equals(approvalStatus))
                .filter(b -> active == null || active.equals("all") ||
                        (active.equals("active") && b.isActive()) ||
                        (active.equals("locked") && !b.isActive()))
                .map(b -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", b.getId());
                    map.put("title", b.getTitle());
                    map.put("author", b.getAuthor());
                    map.put("price", b.getPrice());
                    map.put("category", b.getCategory() != null ? b.getCategory().getName() : "N/A");
                    map.put("stock", b.getStockQuantity());
                    map.put("stockBucket", stockBucket(b.getStockQuantity()));
                    map.put("approvalStatus", b.getApprovalStatus().toString());
                    map.put("active", b.isActive() ? "Active" : "Locked");
                    map.put("seller", b.getSeller() != null ? b.getSeller().getUsername() : "System");
                    return map;
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        
        List<Map<String, Object>> paged = (start > filtered.size()) 
            ? new ArrayList<>() 
            : filtered.subList(start, end);

        return new PageImpl<>(paged, pageable, filtered.size());
    }

    /**
     * Lấy đơn hàng cho admin
     */
    @GetMapping("/orders")
    public Page<Map<String, Object>> getOrdersForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        Pageable pageable = PageRequest.of(page, size);
        List<Order> orders = orderRepository.findAll();

        List<Map<String, Object>> filtered = orders.stream()
                .map(o -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", o.getId());
                    map.put("buyer", o.getBuyer() != null ? o.getBuyer().getUsername() : "Unknown");
                    map.put("total", o.getTotalAmount());
                    map.put("address", o.getShippingAddress());
                    map.put("date", o.getCreatedAt());
                    // Get first sub-order status as main status
                    String orderStatus = o.getSubOrders() == null || o.getSubOrders().isEmpty()
                            ? "N/A"
                            : o.getSubOrders().get(0).getStatus().toString();
                    map.put("status", orderStatus);
                    return map;
                })
                .filter(m -> q == null || q.isEmpty() || 
                        String.valueOf(m.get("id")).contains(q) || 
                        lower(String.valueOf(m.get("buyer"))).contains(lower(q)))
                .filter(m -> status == null || status.equals("all") || 
                        String.valueOf(m.get("status")).equals(status))
                // Note: Date filtering can be added here if needed, 
                // but for now keeping it simple as per initial requirements
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        
        List<Map<String, Object>> paged = (start > filtered.size()) 
            ? new ArrayList<>() 
            : filtered.subList(start, end);

        return new PageImpl<>(paged, pageable, filtered.size());
    }

    /**
     * 📊 Seller Dashboard Stats - API tổng hợp cho Dashboard mới
     * GET /api/panel/seller/dashboard-stats?period=week
     * 
     * Trả về: summaryStats, revenueByDay, orderStatusDistribution,
     *         categoryDistribution, customerGrowth, monthlyRevenue
     */
    @GetMapping("/seller/dashboard-stats")
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

        // Group sub-orders by date (using parentOrder.createdAt)
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

        // Ensure all statuses are present
        for (OrderStatus status : OrderStatus.values()) {
            orderStatusDistribution.putIfAbsent(status.toString(), 0L);
        }

        // ==========================================
        // 4. Category Distribution (books sold by category)
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
        // 5. Customer Growth (new vs returning by month)
        // ==========================================
        // Since User doesn't have createdAt, we approximate using first order date
        Map<String, Map<String, Long>> customerGrowth = new LinkedHashMap<>();
        
        // Get all buyers who ordered from this seller
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

        // Build monthly data for last 6 months
        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = LocalDate.from(now).withDayOfMonth(1).minusMonths(i);
            String monthKey = monthStart.format(DateTimeFormatter.ofPattern("MM/yyyy"));
            
            long newCustomers = 0;
            long returningCustomers = 0;
            
            for (var entry : buyerFirstOrder.entrySet()) {
                Long buyerId = entry.getKey();
                LocalDateTime firstOrderDate = entry.getValue();
                
                // Check if first order was in this month
                if (firstOrderDate.toLocalDate().getYear() == monthStart.getYear()
                        && firstOrderDate.toLocalDate().getMonth() == monthStart.getMonth()) {
                    newCustomers++;
                } else if (firstOrderDate.isBefore(monthStart.atStartOfDay())) {
                    // Check if this buyer had orders in this month
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
        // 6. Monthly Revenue (for line chart)
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

        // ==========================================
        // Build response
        // ==========================================
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summaryStats", summaryStats);
        response.put("revenueByDay", revenueByDay);
        response.put("orderStatusDistribution", orderStatusDistribution);
        response.put("categoryDistribution", categoryDistribution);
        response.put("customerGrowth", customerGrowth);
        response.put("monthlyRevenue", monthlyRevenue);

        return response;
    }
}
