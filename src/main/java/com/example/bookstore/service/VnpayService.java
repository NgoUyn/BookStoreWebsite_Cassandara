package com.example.bookstore.service;

import com.example.bookstore.dto.PaymentInitRequest;
import com.example.bookstore.dto.PaymentInitResponse;
import com.example.bookstore.model.Order;
import com.example.bookstore.model.PaymentTransaction;
import com.example.bookstore.model.enums.PaymentMethod;
import com.example.bookstore.model.enums.PaymentStatus;
import com.example.bookstore.repository.OrderRepository;
import com.example.bookstore.repository.PaymentTransactionRepository;
import com.example.bookstore.utils.VnpayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayService {

    @Value("${vnpay.tmn-code:}")
    private String vnpayTmnCode;

    @Value("${vnpay.hash-secret:}")
    private String vnpayHashSecret;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpayPayUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/payment/vnpay-callback}")
    private String vnpayReturnUrl;

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Tạo request thanh toán VNPay
     */
    @Transactional
    public PaymentInitResponse initiateVnpayPayment(PaymentInitRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Kiểm tra xem đã có transaction nào chưa
        Optional<PaymentTransaction> existingTransaction = paymentTransactionRepository.findByOrderId(order.getId());
        if (existingTransaction.isPresent()) {
            PaymentTransaction existing = existingTransaction.get();
            if (existing.getStatus() == PaymentStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already paid");
            }
            if (existing.getStatus() == PaymentStatus.PENDING) {
                // Return existing payment URL if still valid
                if (existing.getExpiredAt().isAfter(java.time.LocalDateTime.now())) {
                    return PaymentInitResponse.builder()
                            .transactionId(existing.getId())
                            .orderId(order.getId())
                            .amount(existing.getAmount())
                            .paymentUrl(existing.getPaymentUrl())
                            .transactionCode(existing.getTransactionCode())
                            .message("Payment link already generated")
                            .build();
                }
            }
        }

        // Create payment transaction
        String transactionCode = generateTransactionCode(order.getId());
        Long amount = Math.round(order.getTotalAmount()); // Convert to Long (VND)

        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .amount(amount)
                .method(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .transactionCode(transactionCode)
                .build();

        transaction = paymentTransactionRepository.save(transaction);

        // Build VNPay URL
        String paymentUrl = buildVnpayUrl(order, transaction, request.getReturnUrl());

        transaction.setPaymentUrl(paymentUrl);
        transaction = paymentTransactionRepository.save(transaction);

        log.info("VNPay payment initiated: Order={}, Amount={}, TxnCode={}", 
                order.getId(), amount, transactionCode);

        return PaymentInitResponse.builder()
                .transactionId(transaction.getId())
                .orderId(order.getId())
                .amount(amount)
                .paymentUrl(paymentUrl)
                .transactionCode(transactionCode)
                .message("Payment URL generated successfully")
                .build();
    }

    /**
     * Xây dựng URL thanh toán VNPay
     */
    private String buildVnpayUrl(Order order, PaymentTransaction transaction, String returnUrl) {
        Map<String, String> vnp_Params = new LinkedHashMap<>();

        // Bắt buộc
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpayTmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(transaction.getAmount() * 100)); // VNPay uses cents
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", transaction.getTransactionCode()); // Unique transaction reference
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang #" + order.getId());
        vnp_Params.put("vnp_OrderType", "billpayment");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl != null ? returnUrl : vnpayReturnUrl);
        vnp_Params.put("vnp_IpAddr", "127.0.0.1"); // Should be client IP in production
        vnp_Params.put("vnp_CreateDate", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        // Tính toán secure hash
        String queryUrl = VnpayUtil.buildQueryUrl(vnp_Params);
        String secureHash = VnpayUtil.hashAllFields(vnp_Params, vnpayHashSecret);

        return vnpayPayUrl + "?" + queryUrl + "&vnp_SecureHash=" + secureHash;
    }

    /**
     * Xử lý callback từ VNPay
     */
    @Transactional
    public Map<String, Object> handleVnpayCallback(Map<String, String> params) {
        Map<String, Object> response = new HashMap<>();

        try {
            String transactionCode = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String transactionStatus = params.get("vnp_TransactionStatus");
            String secureHash = params.get("vnp_SecureHash");

            // Verify secure hash
            Map<String, String> hashParams = new LinkedHashMap<>(params);
            hashParams.remove("vnp_SecureHash");
            String calculatedHash = VnpayUtil.hashAllFields(hashParams, vnpayHashSecret);

            if (!calculatedHash.equals(secureHash)) {
                log.warn("Invalid secure hash for transaction: {}", transactionCode);
                response.put("RspCode", "97"); // Invalid signature
                response.put("Message", "Invalid signature");
                return response;
            }

            PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(transactionCode)
                    .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionCode));

            // Check if payment was successful (00 = success)
            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                transaction.setStatus(PaymentStatus.COMPLETED);
                transaction.setPaidAt(java.time.LocalDateTime.now());
                transaction.setResponseCode(responseCode);
                transaction.setResponseMessage("Thanh toán thành công");

                paymentTransactionRepository.save(transaction);

                // Update order status to PROCESSING (after payment)
                Order order = transaction.getOrder();
                // Note: Update order status here if needed
                orderRepository.save(order);

                log.info("VNPay payment success: Order={}, TxnCode={}", 
                        transaction.getOrder().getId(), transactionCode);

                response.put("RspCode", "00");
                response.put("Message", "Thanh toán thành công");
            } else {
                transaction.setStatus(PaymentStatus.FAILED);
                transaction.setResponseCode(responseCode);
                transaction.setFailureReason("VNPay response: " + responseCode);

                paymentTransactionRepository.save(transaction);

                log.warn("VNPay payment failed: Order={}, TxnCode={}, ResponseCode={}", 
                        transaction.getOrder().getId(), transactionCode, responseCode);

                response.put("RspCode", "01");
                response.put("Message", "Thanh toán thất bại");
            }
        } catch (Exception e) {
            log.error("Error processing VNPay callback", e);
            response.put("RspCode", "99");
            response.put("Message", "Error processing callback: " + e.getMessage());
        }

        return response;
    }

    /**
     * Kiểm tra trạng thái thanh toán
     */
    public PaymentTransaction getPaymentStatus(Long orderId) {
        return paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "No payment transaction found for order"));
    }

    /**
     * Tạo mã transaction duy nhất
     */
    private String generateTransactionCode(Long orderId) {
        // Format: ORD<orderId><timestamp>
        // Example: ORD123456789123456789
        long timestamp = System.currentTimeMillis() % 1000000; // Last 6 digits of timestamp
        return String.format("ORD%d%d", orderId, timestamp);
    }

    /**
     * Hủy thanh toán (refund)
     */
    @Transactional
    public void cancelPayment(Long orderId) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "No payment transaction found"));

        if (transaction.getStatus() == PaymentStatus.COMPLETED) {
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setResponseMessage("Đã hoàn tiền");
            paymentTransactionRepository.save(transaction);

            log.info("Payment refunded: Order={}, TxnCode={}", orderId, transaction.getTransactionCode());
        }
    }
}
