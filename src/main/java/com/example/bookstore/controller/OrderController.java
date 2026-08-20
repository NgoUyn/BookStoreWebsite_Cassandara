package com.example.bookstore.controller;

import com.example.bookstore.dto.CheckoutMeRequest;
import com.example.bookstore.dto.CheckoutRequest;
import com.example.bookstore.dto.CheckoutResponse;
import com.example.bookstore.dto.OrderDetailResponse;
import com.example.bookstore.dto.SubOrderSummaryResponse;
import com.example.bookstore.dto.OrderFilterRequest;
import com.example.bookstore.dto.OrderFilterResponse;
import com.example.bookstore.dto.OrderSummaryResponse;
import com.example.bookstore.dto.SubOrderFilterRequest;
import com.example.bookstore.dto.SubOrderFilterResponse;
import com.example.bookstore.model.Order;
import com.example.bookstore.model.enums.OrderStatus;
import com.example.bookstore.security.AuthenticationUtil;
import com.example.bookstore.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return orderService.checkoutFromCart(request);
    }

    @PostMapping("/me/checkout")
        @PreAuthorize("hasAuthority('BUYER')")
    public CheckoutResponse checkoutForCurrentBuyer(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @Valid @RequestBody CheckoutMeRequest request,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.checkoutFromCurrentBuyer(buyerId, request.getShippingAddress(), request.getCouponCode());
    }


    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("hasPermission(#buyerId, 'User', 'read')")
    public List<Order> getBuyerOrders(@PathVariable Long buyerId) {
        return orderService.getBuyerOrders(buyerId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('BUYER')")
    public List<Order> getCurrentBuyerOrders(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.getCurrentBuyerOrders(buyerId);
    }

    @PostMapping("/me/filter/summary")
    @PreAuthorize("hasAuthority('BUYER')")
    public List<OrderSummaryResponse> getCurrentBuyerOrderSummaries(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.getCurrentBuyerOrderSummaries(buyerId);
    }

    @GetMapping("/me/{orderId}")
    @PreAuthorize("hasPermission(#orderId, 'Order', 'read')")
    public OrderDetailResponse getCurrentBuyerOrderDetail(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.getCurrentBuyerOrderDetail(buyerId, orderId);
    }

    @GetMapping("/seller/{sellerId}/sub-orders")
    @PreAuthorize("hasPermission(#sellerId, 'User', 'read')")
    public List<SubOrderSummaryResponse> getSellerSubOrders(
            @PathVariable Long sellerId
    ) {
        return orderService.getSellerSubOrders(sellerId);
    }

    @GetMapping("/seller/me/sub-orders")
    @PreAuthorize("hasAuthority('SELLER')")
    public List<SubOrderSummaryResponse> getCurrentSellerSubOrders(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        return orderService.getSellerSubOrders(sellerId);
    }

    @PatchMapping("/sub-orders/{subOrderId}/status")
    @PreAuthorize("hasPermission(#subOrderId, 'SubOrder', 'status')")
    public SubOrderSummaryResponse updateSubOrderStatus(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable Long subOrderId,
            @RequestParam OrderStatus status,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        return orderService.updateSubOrderStatusForSeller(sellerId, subOrderId, status);
    }

    /**
     * Seller xác nhận đơn hàng - tự động chuyển trạng thái theo luồng:
     * - PROCESSING  -> COMFIRMED  (xác nhận đơn)
     * - COMFIRMED   -> SHIPPING   (xác nhận đang giao)
     * - SHIPPING    -> COMPLETED  (xác nhận hoàn thành)
     * 
     * Frontend chỉ cần gọi API này, không cần gửi trạng thái đích.
     * Backend tự tính trạng thái tiếp theo dựa trên trạng thái hiện tại.
     */
    @PostMapping("/sub-orders/{subOrderId}/confirm")
    @PreAuthorize("hasPermission(#subOrderId, 'SubOrder', 'status')")
    public SubOrderSummaryResponse confirmSubOrder(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable Long subOrderId,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        return orderService.confirmSubOrderForSeller(sellerId, subOrderId);
    }

    @PatchMapping("/me/{orderId}/cancel")
    @PreAuthorize("hasPermission(#orderId, 'Order', 'cancel')")
    public OrderSummaryResponse cancelCurrentBuyerOrder(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.cancelCurrentBuyerOrder(buyerId, orderId);
    }

    /**
     * Filter buyer's orders with flexible filtering options
     * 
     * Example: GET /api/orders/me/filter?page=0&pageSize=10&sortBy=createdAt&sortDirection=DESC
     */
    @PostMapping("/me/filter")
        @PreAuthorize("hasAuthority('BUYER')")
    public OrderFilterResponse filterMyOrders(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @RequestBody(required = false) OrderFilterRequest filter,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        if (filter == null) {
            filter = new OrderFilterRequest();
        }
        return orderService.filterBuyerOrders(buyerId, filter);
    }

    /**
     * Get buyer's orders by specific status
     * 
     * Example: GET /api/orders/me/status/COMPLETED
     */
    @GetMapping("/me/status/{status}")
        @PreAuthorize("hasAuthority('BUYER')")
    public List<OrderSummaryResponse> getMyOrdersByStatus(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable OrderStatus status,
            org.springframework.security.core.Authentication auth
    ) {
        Long buyerId = com.example.bookstore.security.AuthenticationUtil.getCurrentUserId(auth);
        return orderService.getBuyerOrdersByStatus(buyerId, status);
    }

    /**
     * Filter seller's sub-orders with flexible filtering options
     * 
     * Example: POST /api/orders/seller/me/filter
     */
    @PostMapping("/seller/me/filter")
    @PreAuthorize("hasAuthority('SELLER')")
    public SubOrderFilterResponse filterMySubOrders(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @RequestBody(required = false) SubOrderFilterRequest filter,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        if (filter == null) {
            filter = new SubOrderFilterRequest();
        }
        return orderService.filterSellerSubOrders(sellerId, filter);
    }

    /**
     * Get seller's sub-orders by specific status
     * 
     * Example: GET /api/orders/seller/me/status/CONFIRMED
     */
    @GetMapping("/seller/me/status/{status}")
    @PreAuthorize("hasAuthority('SELLER')")
    public List<SubOrderSummaryResponse> getMySubOrdersByStatus(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @PathVariable OrderStatus status,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        return orderService.getSellerSubOrdersByStatus(sellerId, status);
    }

    /**
     * Search seller's sub-orders by buyer name
     * 
     * Example: GET /api/orders/seller/me/search?buyerName=john
     */
    @GetMapping("/seller/me/search")
    @PreAuthorize("hasAuthority('SELLER')")
    public List<SubOrderSummaryResponse> searchMySubOrdersByBuyer(
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal,
            @RequestParam(required = true) String buyerName,
            org.springframework.security.core.Authentication auth
    ) {
        Long sellerId = com.example.bookstore.security.AuthenticationUtil.getCurrentSellerId(auth);
        return orderService.searchSellerSubOrdersByBuyer(sellerId, buyerName);
    }
}
