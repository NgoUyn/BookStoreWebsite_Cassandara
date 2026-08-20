# 💳 VNPay Payment Integration Guide

## Overview

This document explains how to integrate VNPay payment gateway into the BookStore checkout flow.

## Architecture

```
┌─────────────┐      POST /api/payment/vnpay/init
│   Frontend  ├─────────────────────────────────────────┐
└─────────────┘                                         │
       │                                                 ▼
       │                          ┌──────────────────────────────────┐
       │                          │   PaymentController              │
       │                          ├──────────────────────────────────┤
       │                          │ • initiateVnpayPayment()         │
       │                          │ • vnpayCallback()                │
       │                          │ • getPaymentStatus()             │
       │                          └────────────┬─────────────────────┘
       │                                       │
       │                                       ▼
       │                          ┌──────────────────────────────────┐
       │                          │   VnpayService                   │
       │                          ├──────────────────────────────────┤
       │                          │ • initiateVnpayPayment()         │
       │                          │ • buildVnpayUrl()                │
       │                          │ • handleVnpayCallback()          │
       │                          │ • cancelPayment()                │
       │                          └────────────┬─────────────────────┘
       │                                       │
       │                                       ▼
       │                          ┌──────────────────────────────────┐
       │                          │ PaymentTransaction Entity        │
       │                          │ (Database)                       │
       │                          └──────────────────────────────────┘
       │
       │  Redirect to VNPay
       └──────────────────────────► VNPay Payment Gateway
                                   │
                          (Customer pays here)
                                   │
                                   ▼
                          GET /api/payment/vnpay-callback
                          (Redirect back with status)
```

## Configuration

### 1. Setup application.properties

```properties
# ===============================
# CẤU HÌNH VNPAY PAYMENT GATEWAY
# ===============================
vnpay.tmn-code=YOUR_TMN_CODE          # Mã thương nhân từ VNPay
vnpay.hash-secret=YOUR_HASH_SECRET    # Mã bí mật từ VNPay
vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
# For production: https://payment.vnpayment.vn/paymentv2/vpcpay.html
vnpay.return-url=http://localhost:8080/api/payment/vnpay-callback
# Production: https://yourdomain.com/api/payment/vnpay-callback
```

### 2. Obtain VNPay Credentials

1. Register merchant account at: https://merchant.vnpayment.vn/
2. Get TMN Code and Secret Key from admin panel
3. Update application.properties with credentials
4. Test with sandbox endpoint first

## API Endpoints

### 1. Initiate Payment

**Request:**
```http
POST /api/payment/vnpay/init
Content-Type: application/json
Authorization: Bearer {token}

{
  "orderId": 123,
  "paymentMethod": "VNPAY",
  "returnUrl": "http://yourfrontend.com/checkout/payment-result"
}
```

**Response:**
```json
{
  "transactionId": 1,
  "orderId": 123,
  "amount": 1000000,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
  "transactionCode": "ORD1231234567890",
  "message": "Payment URL generated successfully"
}
```

**Frontend Usage:**
```javascript
// 1. Call init endpoint
const response = await fetch('/api/payment/vnpay/init', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({
    orderId: orderId,
    paymentMethod: 'VNPAY',
    returnUrl: `${window.location.origin}/main/payment-result`
  })
});

const data = await response.json();

// 2. Redirect to VNPay
if (data.paymentUrl) {
  window.location.href = data.paymentUrl;
}
```

### 2. Handle Callback (VNPay → Server)

**Request:** (Automatic redirect from VNPay)
```http
GET /api/payment/vnpay-callback?vnp_TxnRef=ORD123&vnp_ResponseCode=00&vnp_SecureHash=...
```

**Process:**
1. VNPay redirects customer to callback URL
2. Server validates secure hash
3. Server updates payment status in database
4. Server responds with status

**Response:**
```json
{
  "RspCode": "00",
  "Message": "Thanh toán thành công"
}
```

### 3. Check Payment Status

**Request:**
```http
GET /api/payment/status/123
Authorization: Bearer {token}
```

**Response:**
```json
{
  "id": 1,
  "orderId": 123,
  "amount": 1000000,
  "method": "VNPAY",
  "status": "COMPLETED",
  "transactionCode": "ORD1231234567890",
  "paidAt": "2026-05-20T10:30:45",
  "responseMessage": "Thanh toán thành công"
}
```

### 4. Cancel/Refund Payment

**Request:**
```http
POST /api/payment/cancel/123
Authorization: Bearer {token}
```

**Response:**
```json
{
  "status": "success",
  "message": "Payment cancelled and refunded"
}
```

## Payment Status Flow

```
PENDING          ──────────────────────► (Waiting for payment)
  │
  ├─→ PROCESSING (VNPay processing)
  │
  ├─→ COMPLETED (✅ Payment successful)
  │
  └─→ FAILED (❌ Payment failed)
      │
      └─→ CANCELLED (Manual cancellation)
      
      └─→ REFUNDED (After refund request)
```

## Integration Steps

### Frontend Integration

**1. Update Checkout_Page.html Payment Method Selection:**

```html
<div class="relative">
    <input type="radio" name="payment" id="pay_vnpay" class="radio-card-input">
    <label for="pay_vnpay" class="radio-card-label bg-white p-4 rounded-xl items-center">
        <div class="radio-circle mr-4"></div>
        <div class="w-12 h-12 bg-red-100 border border-red-300 rounded-lg flex items-center justify-center mr-4 flex-shrink-0 text-red-600 font-bold">VNP</div>
        <div class="flex-grow">
            <h3 class="font-bold text-brand-dark text-base mb-1">Thanh toán qua VNPay</h3>
            <p class="text-sm text-gray-500 font-medium">Hỗ trợ thẻ ngân hàng, ví điện tử</p>
        </div>
    </label>
</div>
```

**2. Update checkout-page.js:**

```javascript
// Handle VNPay payment
const handleVNPayPayment = async () => {
    const placeOrderBtn = document.getElementById('checkout-place-order-btn');
    const revert = showSpinner(placeOrderBtn);

    try {
        // 1. Place order first (create Order + SubOrders)
        const checkout = await ApiService.Order.checkoutFromCurrentBuyer(shippingAddress);
        const orderId = checkout.orderId;

        // 2. Initiate VNPay payment
        const paymentResponse = await fetch('/api/payment/vnpay/init', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ApiService.getAuth().token}`
            },
            body: JSON.stringify({
                orderId: orderId,
                paymentMethod: 'VNPAY',
                returnUrl: `${window.location.origin}/main/payment-result`
            })
        });

        const paymentData = await paymentResponse.json();

        // 3. Redirect to VNPay
        if (paymentData.paymentUrl) {
            window.location.href = paymentData.paymentUrl;
        } else {
            alert('Lỗi: ' + paymentData.message);
        }
    } catch (error) {
        alert('Lỗi thanh toán: ' + error.message);
    } finally {
        revert();
    }
};

// Attach to button
placeOrderBtn?.addEventListener('click', () => {
    const paymentMethod = document.querySelector('input[name="payment"]:checked').id;
    if (paymentMethod === 'pay_vnpay') {
        handleVNPayPayment();
    } else if (paymentMethod === 'pay_cod') {
        // Handle COD...
    }
});
```

**3. Create Payment Result Page:**

Create `src/main/resources/templates/main/Payment_Result.html`:

```html
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>Kết quả thanh toán</title>
</head>
<body>
    <div id="payment-result-container">
        <p>Đang xử lý kết quả thanh toán...</p>
    </div>

    <script src="/js/api-service.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', async () => {
            const params = new URLSearchParams(window.location.search);
            const responseCode = params.get('vnp_ResponseCode');
            const orderId = params.get('orderId');

            if (responseCode === '00') {
                // Payment successful
                alert('✅ Thanh toán thành công!');
                window.location.href = `/main/order-details?orderId=${orderId}`;
            } else {
                // Payment failed
                alert('❌ Thanh toán thất bại. Mã lỗi: ' + responseCode);
                window.location.href = '/main/checkout';
            }
        });
    </script>
</body>
</html>
```

## Testing

### Sandbox Testing

1. Use sandbox endpoint: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`
2. Test card: `4111111111111111`
3. Test expiry: Any future date (e.g., 12/25)
4. Test OTP: `123456`

### Production Setup

1. Switch to production endpoint
2. Get live credentials from VNPay
3. Update application.properties
4. Set returnUrl to your production domain
5. Test end-to-end flow

## Error Handling

| Response Code | Meaning | Action |
|---|---|---|
| 00 | Success | Update order status to PROCESSING |
| 01 | Bank declined | Retry payment |
| 02 | Invalid card | Verify card details |
| 97 | Invalid signature | Check secret key |
| 99 | System error | Retry transaction |

## Security Best Practices

1. **Secure Hash Verification**: Always verify `vnp_SecureHash` in callback
2. **HTTPS Only**: Use HTTPS for all callbacks
3. **Timeout Handling**: Payment links expire after 15 minutes
4. **Idempotency**: Handle duplicate callbacks gracefully
5. **Log Transactions**: Keep transaction logs for audit trail

## Next Steps

1. ✅ Create PaymentTransaction entity
2. ✅ Create VnpayService
3. ✅ Create PaymentController
4. ✅ Create database migration
5. ⏳ Integrate frontend with checkout page
6. ⏳ Test payment flow end-to-end
7. ⏳ Switch to production credentials
