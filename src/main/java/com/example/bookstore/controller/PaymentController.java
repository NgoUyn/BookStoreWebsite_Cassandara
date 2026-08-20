package com.example.bookstore.controller;

import com.example.bookstore.dto.PaymentInitRequest;
import com.example.bookstore.dto.PaymentInitResponse;
import com.example.bookstore.model.PaymentTransaction;
import com.example.bookstore.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final VnpayService vnpayService;

    /**
     * Khởi tạo thanh toán VNPay
     * POST /api/payment/vnpay/init
     */
    @PostMapping("/vnpay/init")
    @PreAuthorize("isAuthenticated()")
    public PaymentInitResponse initiateVnpayPayment(
            @Valid @RequestBody PaymentInitRequest request,
            @AuthenticationPrincipal com.example.bookstore.security.JwtAuthenticatedPrincipal principal) {
        
        log.info("Payment init request: Order={}, Method={}", 
                request.getOrderId(), request.getPaymentMethod());
        
        return vnpayService.initiateVnpayPayment(request);
    }

    /**
     * Callback từ VNPay (GET request)
     * VNPay sẽ redirect về đây sau khi khách hàng thanh toán
     */
    @GetMapping("/vnpay-callback")
    public void vnpayCallback(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Map<String, String> params = extractVnpayParams(request);
        
        log.info("VNPay callback received: TxnRef={}, ResponseCode={}", 
                params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));
        
        // Process callback (update database if needed)
        vnpayService.handleVnpayCallback(params);
        
        // Redirect to frontend result page based on response code
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String orderId = params.get("vnp_TxnRef");

        String resultUrl;
        if ("00".equals(vnp_ResponseCode)) {
            // Success
            resultUrl = "/main/payment-result?status=success&orderId=" + orderId;
        } else {
            // Failed
            resultUrl = "/main/payment-result?status=failed&errorCode=" + vnp_ResponseCode;
        }
        
        response.sendRedirect(resultUrl);
    }

    /**
     * Kiểm tra trạng thái thanh toán
     * GET /api/payment/status/{orderId}
     */
    @GetMapping("/status/{orderId}")
    @PreAuthorize("hasPermission(#orderId, 'Order', 'read')")
    public com.example.bookstore.dto.PaymentStatusResponse getPaymentStatus(@PathVariable Long orderId) {
        PaymentTransaction transaction = vnpayService.getPaymentStatus(orderId);
        return com.example.bookstore.dto.PaymentStatusResponse.fromPaymentTransaction(transaction);
    }

    /**
     * Hủy thanh toán (hoàn tiền)
     * POST /api/payment/cancel/{orderId}
     */
    @PostMapping("/cancel/{orderId}")
    @PreAuthorize("hasPermission(#orderId, 'Order', 'write')")
    public Map<String, String> cancelPayment(@PathVariable Long orderId) {
        vnpayService.cancelPayment(orderId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Payment cancelled and refunded");
        
        return response;
    }

    /**
     * Trích xuất tham số từ VNPay callback
     */
    private Map<String, String> extractVnpayParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramValue = request.getParameter(paramName);
            if (paramValue != null && !paramValue.isEmpty()) {
                params.put(paramName, paramValue);
            }
        }
        
        return params;
    }
}
