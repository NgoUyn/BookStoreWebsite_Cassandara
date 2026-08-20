package com.example.bookstore.service;

import com.example.bookstore.dto.CheckoutRequest;
import com.example.bookstore.dto.CheckoutResponse;
import com.example.bookstore.dto.OrderDetailResponse;
import com.example.bookstore.dto.SubOrderSummaryResponse;
import com.example.bookstore.dto.OrderFilterRequest;
import com.example.bookstore.dto.OrderFilterResponse;
import com.example.bookstore.dto.OrderSummaryResponse;
import com.example.bookstore.dto.SubOrderFilterRequest;
import com.example.bookstore.dto.SubOrderFilterResponse;
import com.example.bookstore.model.*;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.CartRepository;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.SubOrderRepository;
import com.example.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import com.example.bookstore.dto.OrderCompletedEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final CouponService couponService;
    private final com.example.bookstore.service.NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.recommendation.exchange:recommendation_exchange}")
    private String recommendationExchange;

    @Value("${app.rabbitmq.recommendation.routing-key:recommendation_routing_key}")
    private String recommendationRoutingKey;
    @Transactional
    public CheckoutResponse checkoutFromCart(CheckoutRequest request) {
        return checkoutInternal(request.getBuyerId(), request.getShippingAddress(), request.getCouponCode());
    }

    @Transactional
    public CheckoutResponse checkoutFromCurrentBuyer(Long buyerId, String shippingAddress, String couponCode) {
        return checkoutInternal(buyerId, shippingAddress, couponCode);
    }


    private CheckoutResponse checkoutInternal(Long buyerId, String shippingAddress, String couponCode) {
        User buyer = userRepository.findById(buyerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        if (buyer.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a buyer");
        }

        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buyer has no cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        Map<User, List<CartItem>> itemsBySeller = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            Book book = item.getBook();
            if (book == null || book.getSeller() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book or seller data is invalid in cart");
            }
            itemsBySeller.computeIfAbsent(book.getSeller(), key -> new ArrayList<>()).add(item);
        }

        Order order = Order.builder()
                .buyer(buyer)
                .shippingAddress(shippingAddress)
                .totalAmount(0.0)
                .discountAmount(0.0)
                .build();

        List<SubOrder> subOrders = new ArrayList<>();
        double orderTotal = 0.0;

        for (Map.Entry<User, List<CartItem>> entry : itemsBySeller.entrySet()) {
            User seller = entry.getKey();
            List<CartItem> sellerItems = entry.getValue();

            SubOrder subOrder = SubOrder.builder()
                    .parentOrder(order)
                    .seller(seller)
                    .status(OrderStatus.PROCESSING)
                    .subTotal(0.0)
                    .build();

            List<OrderItem> orderItems = new ArrayList<>();
            double subTotal = 0.0;

            for (CartItem cartItem : sellerItems) {
                Book book = cartItem.getBook();
                Integer quantity = cartItem.getQuantity();
                if (quantity == null || quantity <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cart item quantity");
                }

                if (book.getApprovalStatus() != ApprovalStatus.APPROVED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains unapproved book");
                }

                if (book.getStockQuantity() == null || quantity > book.getStockQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart quantity exceeds stock");
                }

                double unitPrice = book.getPrice() == null ? 0.0 : book.getPrice();
                subTotal += unitPrice * quantity;

                OrderItem orderItem = OrderItem.builder()
                        .subOrder(subOrder)
                        .book(book)
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .build();
                orderItems.add(orderItem);
            }

            subOrder.setSubTotal(subTotal);
            subOrder.setItems(orderItems);
            subOrders.add(subOrder);
            orderTotal += subTotal;
        }

        // Handle Coupon with cross-seller validation
        double originalTotal = orderTotal;
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            // Extract seller IDs from cart to validate coupon ownership
            List<Long> sellerIdsInCart = itemsBySeller.keySet().stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            // Validate coupon against sellers in cart (prevents cross-seller usage)
            Coupon coupon = couponService.validateCouponForSellerList(
                    couponCode.trim(), sellerIdsInCart, (int) orderTotal);

            Integer discount = coupon.calculateDiscount((int) orderTotal);
            order.setCouponCode(couponCode.trim().toUpperCase());
            order.setDiscountAmount((double) discount);
            orderTotal = Math.max(0, orderTotal - discount);

            // Mark coupon as used
            couponService.useCoupon(couponCode);
        }

        // Compute shipping fee (flat rate for now)
        double shippingFee = 30000.0;
        order.setShippingFee(shippingFee);
        order.setTotalAmount(orderTotal + shippingFee);
        order.setSubOrders(subOrders);

        Order saved = orderRepository.save(order);
        // BẮN EVENT REAL-TIME GỢI Ý
        try {
            List<Long> purchasedBookIds = new ArrayList<>();
            for (CartItem item : cart.getItems()) {
                purchasedBookIds.add(item.getBook().getId());
            }
            // Chỉ gửi nếu giỏ hàng có từ 2 sản phẩm trở lên (mới tạo thành cặp được)
            if (purchasedBookIds.size() > 1) {
                OrderCompletedEvent event = new OrderCompletedEvent(saved.getId(), purchasedBookIds);
                rabbitTemplate.convertAndSend(recommendationExchange, recommendationRoutingKey, event);
                log.info("Đã gửi event đơn hàng {} lên RabbitMQ để tính toán gợi ý Real-time", saved.getId());
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi RabbitMQ event, nhưng đơn hàng vẫn thành công: {}", e.getMessage());
        }
        cart.getItems().clear();
        cartRepository.save(cart);

        notifyOrderCreated(saved, buyer, itemsBySeller);

        return CheckoutResponse.builder()
                .orderId(saved.getId())
                .buyerId(buyer.getId())
                .shippingAddress(saved.getShippingAddress())
            .totalAmount(saved.getTotalAmount())
            .shippingFee(saved.getShippingFee())
                .originalAmount(originalTotal)
                .discountAmount(saved.getDiscountAmount())
                .couponCode(saved.getCouponCode())
                .subOrderCount(saved.getSubOrders() == null ? 0 : saved.getSubOrders().size())
                .build();

    }

    @Transactional(readOnly = true)
    public List<Order> getBuyerOrders(Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
    }

    @Transactional(readOnly = true)
    public List<Order> getCurrentBuyerOrders(Long buyerId) {
        return getBuyerOrders(buyerId);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getCurrentBuyerOrderSummaries(Long buyerId) {
        return getBuyerOrders(buyerId).stream()
            .map(this::toOrderSummary)
            .collect(Collectors.toList());
    }

    @Transactional
    public OrderSummaryResponse cancelCurrentBuyerOrder(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!canBuyerCancelOrder(order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể hủy đơn hàng ở trạng thái đang xác nhận");
        }

        if (order.getSubOrders() != null) {
            for (SubOrder subOrder : order.getSubOrders()) {
                subOrder.setStatus(OrderStatus.CANCELLED);
            }
            subOrderRepository.saveAll(order.getSubOrders());
        }

        notifyOrderCancelled(order, buyerId);

        return toOrderSummary(order);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getCurrentBuyerOrderDetail(Long buyerId, Long orderId) {
        User buyer = userRepository.findById(buyerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        List<OrderDetailResponse.SubOrderDetail> subOrderDetails = new ArrayList<>();
        List<OrderDetailResponse.OrderItemFlat> flatItems = new ArrayList<>();
        int totalItemsCount = 0;

        if (order.getSubOrders() != null) {
            for (SubOrder subOrder : order.getSubOrders()) {
                List<OrderDetailResponse.OrderItemDetail> items = new ArrayList<>();
                if (subOrder.getItems() != null) {
                    for (OrderItem item : subOrder.getItems()) {
                        items.add(OrderDetailResponse.OrderItemDetail.builder()
                                .id(item.getId())
                                .bookTitle(item.getBook() != null ? item.getBook().getTitle() : "N/A")
                                .price(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .subtotal(item.getUnitPrice() * item.getQuantity())
                                .build());
                    // add flat item for frontend
                    flatItems.add(OrderDetailResponse.OrderItemFlat.builder()
                        .bookId(item.getBook() == null ? null : item.getBook().getId())
                        .title(item.getBook() == null ? "N/A" : item.getBook().getTitle())
                        .author(item.getBook() == null ? "" : item.getBook().getAuthor())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getUnitPrice() * item.getQuantity())
                        .sellerName(subOrder.getSeller() == null ? "N/A" : (subOrder.getSeller().getShopName() == null ? subOrder.getSeller().getUsername() : subOrder.getSeller().getShopName()))
                        .subOrderStatus(subOrder.getStatus() == null ? null : subOrder.getStatus().toString())
                        .build());
                    totalItemsCount += item.getQuantity() == null ? 0 : item.getQuantity();
                    }
                }

                subOrderDetails.add(OrderDetailResponse.SubOrderDetail.builder()
                        .id(subOrder.getId())
                        .sellerName(subOrder.getSeller() != null ? subOrder.getSeller().getUsername() : "N/A")
                        .status(subOrder.getStatus().toString())
                        .subTotal(subOrder.getSubTotal())
                        .items(items)
                        .build());
            }
        }

        return OrderDetailResponse.builder()
                .id(order.getId())
                .buyerName(buyer.getUsername())
                .buyerUsername(buyer.getUsername())
                .buyerId(buyer.getId())
                .buyerEmail(buyer.getUsername() + "@bookom.vn")
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .subOrders(subOrderDetails)
                .items(flatItems)
                .totalItems(totalItemsCount)
                .build();
    }

    /**
     * Lấy đơn hàng với filters (for admin)
     */
    public Page<Order> getOrdersWithFilters(
            int page, int size, String q, String status,
            LocalDate dateFrom, LocalDate dateTo
    ) {
        Pageable pageable = PageRequest.of(page, size);

        if (q != null && !q.isEmpty()) {
            return orderRepository.searchOrders(q, pageable);
        }

        return orderRepository.findAll(pageable);
    }

    /**
     * Lấy chi tiết đơn hàng (for admin)
     */
    public OrderDetailResponse getOrderDetailsForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Đơn hàng không tồn tại"));

        List<OrderDetailResponse.SubOrderDetail> subOrderDetails = order.getSubOrders()
                .stream()
                .map(subOrder -> {
                    List<OrderDetailResponse.OrderItemDetail> items = subOrder.getItems()
                            .stream()
                            .map(item -> OrderDetailResponse.OrderItemDetail.builder()
                                    .id(item.getId())
                                    .bookTitle(item.getBook().getTitle())
                                    .price(item.getUnitPrice())
                                    .quantity(item.getQuantity())
                                    .subtotal(item.getUnitPrice() * item.getQuantity())
                                    .build())
                            .toList();

                    return OrderDetailResponse.SubOrderDetail.builder()
                            .id(subOrder.getId())
                            .sellerName(subOrder.getSeller().getUsername())
                            .status(subOrder.getStatus().toString())
                            .subTotal(subOrder.getSubTotal())
                            .items(items)
                            .build();
                })
                .toList();

        return OrderDetailResponse.builder()
                .id(order.getId())
                .buyerName(order.getBuyer().getUsername())
                .buyerEmail(order.getBuyer().getUsername() + "@bookom.vn")
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .subOrders(subOrderDetails)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SubOrderSummaryResponse> getSellerSubOrders(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));

        return subOrderRepository.findBySellerOrderByIdDesc(seller).stream()
            .map(this::toSubOrderSummary)
                .toList();
    }

    @Transactional
    public SubOrderSummaryResponse updateSubOrderStatusForSeller(Long sellerId, Long subOrderId, OrderStatus status) {
        SubOrder subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sub order not found"));

        subOrder.setStatus(status);
        SubOrder saved = subOrderRepository.save(subOrder);

        // Send notification to buyer about sub-order status change
        try {
            if (saved.getParentOrder() != null && saved.getParentOrder().getBuyer() != null) {
                Long buyerId = saved.getParentOrder().getBuyer().getId();
                com.example.bookstore.dto.NotificationCreateRequest req = new com.example.bookstore.dto.NotificationCreateRequest();
                req.setUserId(buyerId);
                req.setType(com.example.bookstore.model.enums.NotificationType.SUB_ORDER_STATUS_CHANGED);
                req.setTitle("Trạng thái đơn hàng thay đổi");
                req.setMessage(String.format("Sub-order #%d của đơn #%d đã chuyển sang %s", saved.getId(),
                        saved.getParentOrder().getId(), status == null ? "UNKNOWN" : status.name()));
                req.setPayloadJson(String.format("{\"subOrderId\":%d,\"orderId\":%d,\"status\":\"%s\"}",
                        saved.getId(), saved.getParentOrder().getId(), status == null ? "UNKNOWN" : status.name()));
                req.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);

                // fire-and-forget; NotificationService will persist and enqueue delivery with retry
                try {
                    notificationService.createNotification(sellerId, buyerId, req);
                } catch (Exception e) {
                    log.warn("Failed to send notification for sub-order status update (subOrderId={}, status={}): {}", subOrderId, status, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to prepare notification for sub-order status update (subOrderId={}, status={}): {}", subOrderId, status, e.getMessage());
        }

        return toSubOrderSummary(saved);
    }

    /**
     * Seller xác nhận đơn hàng - tự động chuyển trạng thái dựa trên trạng thái hiện tại:
     * - PROCESSING  -> COMFIRMED  (xác nhận đơn)
     * - COMFIRMED   -> SHIPPING   (xác nhận đang giao)
     * - SHIPPING    -> COMPLETED  (xác nhận hoàn thành)
     * Các trạng thái khác -> throw lỗi
     */
    @Transactional
    public SubOrderSummaryResponse confirmSubOrderForSeller(Long sellerId, Long subOrderId) {
        SubOrder subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sub order not found"));

        // Xác định trạng thái tiếp theo dựa trên trạng thái hiện tại
        OrderStatus currentStatus = subOrder.getStatus();
        OrderStatus nextStatus;

        switch (currentStatus) {
            case PROCESSING:
                nextStatus = OrderStatus.COMFIRMED;
                break;
            case COMFIRMED:
                nextStatus = OrderStatus.SHIPPING;
                break;
            case SHIPPING:
                nextStatus = OrderStatus.COMPLETED;
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Không thể xác nhận đơn hàng ở trạng thái '%s'. Chỉ có thể xác nhận đơn ở trạng thái: PROCESSING, COMFIRMED, SHIPPING",
                                currentStatus));
        }

        subOrder.setStatus(nextStatus);
        SubOrder saved = subOrderRepository.save(subOrder);

        // Gửi notification cho buyer
        try {
            if (saved.getParentOrder() != null && saved.getParentOrder().getBuyer() != null) {
                Long buyerId = saved.getParentOrder().getBuyer().getId();
                com.example.bookstore.dto.NotificationCreateRequest req = new com.example.bookstore.dto.NotificationCreateRequest();
                req.setUserId(buyerId);
                req.setType(com.example.bookstore.model.enums.NotificationType.SUB_ORDER_STATUS_CHANGED);
                req.setTitle("Trạng thái đơn hàng thay đổi");
                req.setMessage(String.format("Đơn hàng #%d đã chuyển sang trạng thái: %s",
                        saved.getParentOrder().getId(), getStatusDisplayName(nextStatus)));
                req.setPayloadJson(String.format("{\"subOrderId\":%d,\"orderId\":%d,\"status\":\"%s\"}",
                        saved.getId(), saved.getParentOrder().getId(), nextStatus.name()));
                req.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);

                try {
                    notificationService.createNotification(sellerId, buyerId, req);
                } catch (Exception e) {
                    log.warn("Failed to send notification for sub-order confirm (subOrderId={}, nextStatus={}): {}", subOrderId, nextStatus, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to prepare notification for sub-order confirm (subOrderId={}, nextStatus={}): {}", subOrderId, nextStatus, e.getMessage());
        }

        return toSubOrderSummary(saved);
    }

    /**
     * Helper: Lấy tên hiển thị cho trạng thái
     */
    private String getStatusDisplayName(OrderStatus status) {
        if (status == null) return "Không xác định";
        switch (status) {
            case PROCESSING:      return "Đang xác nhận";
            case COMFIRMED:       return "Đã xác nhận";
            case SHIPPING:        return "Đang giao";
            case COMPLETED:       return "Đã hoàn thành";
            case CANCELLED:       return "Đã hủy";
            default:              return status.name();
        }
    }

    private SubOrderSummaryResponse toSubOrderSummary(SubOrder subOrder) {
        User seller = subOrder.getSeller();
        String sellerName = seller == null ? null : (seller.getShopName() == null ? seller.getUsername() : seller.getShopName());

        User buyer = subOrder.getParentOrder() == null ? null : subOrder.getParentOrder().getBuyer();
        String buyerUsername = buyer == null ? null : buyer.getUsername();

        int itemCount = 0;
        List<String> titles = new ArrayList<>();
        List<OrderItem> items = subOrder.getItems();
        if (items != null) {
            for (OrderItem item : items) {
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                itemCount += qty;

                if (titles.size() < 3) {
                    Book book = item.getBook();
                    String title = book == null || book.getTitle() == null ? "Không rõ" : book.getTitle();
                    titles.add(title);
                }
            }
        }

        String itemSummary = titles.isEmpty() ? null : String.join(", ", titles);
        if (items != null && items.size() > titles.size()) {
            itemSummary = itemSummary + " ...";
        }

        return SubOrderSummaryResponse.builder()
                .subOrderId(subOrder.getId())
                .orderId(subOrder.getParentOrder() == null ? null : subOrder.getParentOrder().getId())
                .sellerId(seller == null ? null : seller.getId())
                .sellerName(sellerName)
                .buyerUsername(buyerUsername)
                .itemSummary(itemSummary)
                .itemCount(itemCount)
                .status(subOrder.getStatus())
                .subTotal(subOrder.getSubTotal())
                .totalAmount(subOrder.getSubTotal() != null ? subOrder.getSubTotal() : 0.0)
                .build();
    }

    /**
     * Filter buyer orders with flexible filtering options
     */
    @Transactional(readOnly = true)
    public OrderFilterResponse filterBuyerOrders(Long buyerId, OrderFilterRequest filter) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        // Set default pagination values
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int pageSize = filter.getPageSize() != null ? filter.getPageSize() : 10;
        
        // Validate pagination
        if (page < 0) page = 0;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;

        // Build sort
        Sort.Direction direction = "DESC".equalsIgnoreCase(filter.getSortDirection()) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "createdAt";
        
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(direction, sortBy));

        // Fetch filtered orders
        Page<Order> pageResult = orderRepository.findByBuyerWithFilters(
            buyer,
            filter.getCreatedFrom(),
            filter.getCreatedTo(),
            filter.getMinPrice(),
            filter.getMaxPrice(),
            pageable
        );

        // Convert to response DTOs
        List<OrderSummaryResponse> summaries = pageResult.getContent().stream()
            .map(this::toOrderSummary)
            .collect(Collectors.toList());

        return OrderFilterResponse.builder()
            .orders(summaries)
            .totalCount(pageResult.getTotalElements())
            .currentPage(page)
            .pageSize(pageSize)
            .totalPages(pageResult.getTotalPages())
            .build();
    }

    /**
     * Filter seller's sub-orders with flexible filtering options
     */
    @Transactional(readOnly = true)
    public SubOrderFilterResponse filterSellerSubOrders(Long sellerId, SubOrderFilterRequest filter) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));

        // Set default pagination values
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int pageSize = filter.getPageSize() != null ? filter.getPageSize() : 10;
        
        // Validate pagination
        if (page < 0) page = 0;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;

        // Build sort
        Sort.Direction direction = "DESC".equalsIgnoreCase(filter.getSortDirection()) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "id";
        
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(direction, sortBy));

        // Fetch filtered sub-orders
        Page<SubOrder> pageResult = subOrderRepository.findBySellerWithFilters(
            seller,
            filter.getStatus(),
            filter.getCreatedFrom(),
            filter.getCreatedTo(),
            filter.getMinPrice(),
            filter.getMaxPrice(),
            pageable
        );

        // Convert to response DTOs
        List<SubOrderSummaryResponse> summaries = pageResult.getContent().stream()
            .map(this::toSubOrderSummary)
            .collect(Collectors.toList());

        return SubOrderFilterResponse.builder()
            .subOrders(summaries)
            .totalCount(pageResult.getTotalElements())
            .currentPage(page)
            .pageSize(pageSize)
            .totalPages(pageResult.getTotalPages())
            .build();
    }

    /**
     * Search seller's sub-orders by buyer name
     */
    @Transactional(readOnly = true)
    public List<SubOrderSummaryResponse> searchSellerSubOrdersByBuyer(Long sellerId, String buyerName) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));

        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buyer name cannot be empty");
        }

        return subOrderRepository.findBySellerAndBuyerNameContaining(seller, buyerName.trim())
            .stream()
            .map(this::toSubOrderSummary)
            .collect(Collectors.toList());
    }

    /**
     * Get orders by status (for buyers)
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getBuyerOrdersByStatus(Long buyerId, OrderStatus status) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));

        List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
        
        return orders.stream()
            .filter(order -> hasOrderStatus(order, status))
            .map(this::toOrderSummary)
            .collect(Collectors.toList());
    }

    /**
     * Get sub-orders by status (for sellers)
     */
    @Transactional(readOnly = true)
    public List<SubOrderSummaryResponse> getSellerSubOrdersByStatus(Long sellerId, OrderStatus status) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));

        return subOrderRepository.findBySellerAndStatusOrdered(seller, status)
            .stream()
            .map(this::toSubOrderSummary)
            .collect(Collectors.toList());
    }

    /**
     * Helper: Convert Order to OrderSummaryResponse
     */
    private OrderSummaryResponse toOrderSummary(Order order) {
        OrderStatus overallStatus = determineOverallOrderStatus(order);
        
        return OrderSummaryResponse.builder()
            .orderId(order.getId())
            .buyerId(order.getBuyer() == null ? null : order.getBuyer().getId())
            .buyerUsername(order.getBuyer() == null ? null : order.getBuyer().getUsername())
            .totalAmount(order.getTotalAmount())
            .createdAt(order.getCreatedAt())
            .subOrderCount(order.getSubOrders() == null ? 0 : order.getSubOrders().size())
            .overallStatus(overallStatus)
            .shippingAddress(order.getShippingAddress())
            .build();
    }

    /**
     * Helper: Determine overall order status based on sub-orders
     */
    private OrderStatus determineOverallOrderStatus(Order order) {
        if (order.getSubOrders() == null || order.getSubOrders().isEmpty()) {
            return OrderStatus.PROCESSING;
        }

        if (order.getSubOrders().stream().allMatch(so -> so.getStatus() == OrderStatus.CANCELLED)) {
            return OrderStatus.CANCELLED;
        }

        // If all sub-orders are completed, order is completed
        if (order.getSubOrders().stream().allMatch(so -> so.getStatus() == OrderStatus.COMPLETED)) {
            return OrderStatus.COMPLETED;
        }

        // If any sub-order is shipping, order is being shipped
        if (order.getSubOrders().stream().anyMatch(so -> so.getStatus() == OrderStatus.SHIPPING)) {
            return OrderStatus.SHIPPING;
        }

        // If any sub-order is processing, order is processing
        if (order.getSubOrders().stream().anyMatch(so -> so.getStatus() == OrderStatus.PROCESSING)) {
            return OrderStatus.PROCESSING;
        }

        return OrderStatus.PROCESSING;
    }

    /**
     * Helper: Check if order has a specific status (by checking sub-orders)
     */
    private boolean hasOrderStatus(Order order, OrderStatus status) {
        if (order.getSubOrders() == null || order.getSubOrders().isEmpty()) {
            return status == OrderStatus.PROCESSING;
        }

        if (status == OrderStatus.CANCELLED || status == OrderStatus.COMPLETED || status == OrderStatus.PROCESSING) {
            return order.getSubOrders().stream()
                .allMatch(subOrder -> subOrder.getStatus() == status);
        }

        return order.getSubOrders().stream()
            .anyMatch(subOrder -> subOrder.getStatus() == status);
    }

    private boolean canBuyerCancelOrder(Order order) {
        if (order.getSubOrders() == null || order.getSubOrders().isEmpty()) {
            return true;
        }

        // Allow cancellation only if all sub-orders are in PROCESSING state
        // Cannot cancel: COMFIRMED, SHIPPING, COMPLETED, CANCELLED
        return order.getSubOrders().stream()
            .allMatch(subOrder -> 
                subOrder.getStatus() == OrderStatus.PROCESSING);
    }

    private void notifyOrderCreated(Order order, User buyer, Map<User, List<CartItem>> itemsBySeller) {
        if (order == null || buyer == null || itemsBySeller == null || itemsBySeller.isEmpty()) {
            return;
        }

        try {
            com.example.bookstore.dto.NotificationCreateRequest buyerReq = new com.example.bookstore.dto.NotificationCreateRequest();
            buyerReq.setType(com.example.bookstore.model.enums.NotificationType.ORDER_CREATED);
            buyerReq.setTitle("Đơn hàng đã được tạo");
            buyerReq.setMessage(String.format("Đơn hàng #%d của bạn đã được tạo thành công.", order.getId()));
            buyerReq.setPayloadJson(String.format("{\"orderId\":%d,\"status\":\"%s\",\"source\":\"checkout\"}",
                    order.getId(), OrderStatus.PROCESSING.name()));
            buyerReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
            notificationService.createNotification(buyer.getId(), buyer.getId(), buyerReq);
        } catch (Exception e) {
            log.warn("Failed to notify buyer about new order (orderId={}): {}", order.getId(), e.getMessage());
        }

        try {
            com.example.bookstore.dto.NotificationCreateRequest adminReq = new com.example.bookstore.dto.NotificationCreateRequest();
            adminReq.setType(com.example.bookstore.model.enums.NotificationType.SYSTEM_ANNOUNCEMENT);
            adminReq.setTitle("Có đơn hàng mới");
            adminReq.setMessage(String.format("Đơn hàng #%d vừa được tạo bởi %s.", order.getId(), buyer.getUsername()));
            adminReq.setPayloadJson(String.format("{\"orderId\":%d,\"buyerId\":%d,\"buyerUsername\":\"%s\",\"status\":\"%s\"}",
                    order.getId(), buyer.getId(), buyer.getUsername().replace("\"", "\\\""), OrderStatus.PROCESSING.name()));
            adminReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
            notificationService.createNotificationForAdmins(buyer.getId(), adminReq);
        } catch (Exception e) {
            log.warn("Failed to notify admins about new order (orderId={}): {}", order.getId(), e.getMessage());
        }

        for (Map.Entry<User, List<CartItem>> entry : itemsBySeller.entrySet()) {
            User seller = entry.getKey();
            if (seller == null) {
                continue;
            }

            double sellerTotal = entry.getValue() == null ? 0.0 : entry.getValue().stream()
                    .mapToDouble(item -> {
                        if (item == null || item.getBook() == null || item.getQuantity() == null) {
                            return 0.0;
                        }
                        Double price = item.getBook().getPrice();
                        return (price == null ? 0.0 : price) * item.getQuantity();
                    })
                    .sum();

            try {
                com.example.bookstore.dto.NotificationCreateRequest sellerReq = new com.example.bookstore.dto.NotificationCreateRequest();
                sellerReq.setType(com.example.bookstore.model.enums.NotificationType.ORDER_CREATED);
                sellerReq.setTitle("Có đơn hàng mới");
                sellerReq.setMessage(String.format("Đơn hàng #%d có sản phẩm của shop bạn, tổng tiền phần shop: %.0f VND.",
                        order.getId(), sellerTotal));
                sellerReq.setPayloadJson(String.format("{\"orderId\":%d,\"sellerId\":%d,\"status\":\"%s\",\"subTotal\":%.0f}",
                        order.getId(), seller.getId(), OrderStatus.PROCESSING.name(), sellerTotal));
                sellerReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
                notificationService.createNotification(buyer.getId(), seller.getId(), sellerReq);
            } catch (Exception e) {
                log.warn("Failed to notify seller about new order (orderId={}, sellerId={}): {}", order.getId(), seller.getId(), e.getMessage());
            }
        }
    }

    private void notifyOrderCancelled(Order order, Long buyerId) {
        if (order == null) {
            return;
        }

        try {
            com.example.bookstore.dto.NotificationCreateRequest buyerReq = new com.example.bookstore.dto.NotificationCreateRequest();
            buyerReq.setType(com.example.bookstore.model.enums.NotificationType.ORDER_STATUS_CHANGED);
            buyerReq.setTitle("Đơn hàng đã bị hủy");
            buyerReq.setMessage(String.format("Đơn hàng #%d đã được hủy.", order.getId()));
            buyerReq.setPayloadJson(String.format("{\"orderId\":%d,\"status\":\"%s\",\"source\":\"buyer_cancel\"}",
                    order.getId(), OrderStatus.CANCELLED.name()));
            buyerReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
            notificationService.createNotification(buyerId, buyerId, buyerReq);
        } catch (Exception e) {
            log.warn("Failed to notify buyer about cancelled order (orderId={}): {}", order.getId(), e.getMessage());
        }

        try {
            com.example.bookstore.dto.NotificationCreateRequest adminReq = new com.example.bookstore.dto.NotificationCreateRequest();
            adminReq.setType(com.example.bookstore.model.enums.NotificationType.SYSTEM_ANNOUNCEMENT);
            adminReq.setTitle("Đơn hàng bị hủy");
            adminReq.setMessage(String.format("Đơn hàng #%d vừa bị buyer hủy.", order.getId()));
            adminReq.setPayloadJson(String.format("{\"orderId\":%d,\"status\":\"%s\",\"source\":\"buyer_cancel\"}",
                    order.getId(), OrderStatus.CANCELLED.name()));
            adminReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
            notificationService.createNotificationForAdmins(buyerId, adminReq);
        } catch (Exception e) {
            log.warn("Failed to notify admins about cancelled order (orderId={}): {}", order.getId(), e.getMessage());
        }

        if (order.getSubOrders() == null) {
            return;
        }

        for (SubOrder subOrder : order.getSubOrders()) {
            User seller = subOrder.getSeller();
            if (seller == null) {
                continue;
            }

            try {
                com.example.bookstore.dto.NotificationCreateRequest sellerReq = new com.example.bookstore.dto.NotificationCreateRequest();
                sellerReq.setType(com.example.bookstore.model.enums.NotificationType.ORDER_STATUS_CHANGED);
                sellerReq.setTitle("Đơn hàng đã bị hủy");
                sellerReq.setMessage(String.format("Đơn hàng #%d chứa hàng của shop bạn đã bị hủy.", order.getId()));
                sellerReq.setPayloadJson(String.format("{\"orderId\":%d,\"subOrderId\":%d,\"status\":\"%s\"}",
                        order.getId(), subOrder.getId(), OrderStatus.CANCELLED.name()));
                sellerReq.setPriority(com.example.bookstore.model.enums.NotificationPriority.NORMAL);
                notificationService.createNotification(buyerId, seller.getId(), sellerReq);
            } catch (Exception e) {
                log.warn("Failed to notify seller about cancelled order (orderId={}, subOrderId={}, sellerId={}): {}",
                        order.getId(), subOrder.getId(), seller.getId(), e.getMessage());
            }
        }
    }
}
