# VNPay Payment - API Testing Examples

## Using Postman or cURL

### 1. Initialize Payment

**Postman Setup:**

```http
POST http://localhost:8080/api/payment/vnpay/init HTTP/1.1
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "orderId": 1,
  "paymentMethod": "VNPAY",
  "returnUrl": "http://localhost:3000/checkout/payment-result"
}
```

**Expected Response:**
```json
{
  "transactionId": 1,
  "orderId": 1,
  "amount": 1000000,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Version=2.1.0&vnp_Command=pay&vnp_TmnCode=YOUR_CODE&...",
  "transactionCode": "ORD11234567890",
  "message": "Payment URL generated successfully"
}
```

**cURL Command:**
```bash
curl -X POST http://localhost:8080/api/payment/vnpay/init \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "orderId": 1,
    "paymentMethod": "VNPAY",
    "returnUrl": "http://localhost:3000/checkout/payment-result"
  }'
```

---

### 2. Check Payment Status

**Postman Setup:**
```http
GET http://localhost:8080/api/payment/status/1 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Expected Response (PENDING):**
```json
{
  "transactionId": 1,
  "orderId": 1,
  "amount": 1000000,
  "method": "Thanh toán VNPay",
  "status": "PENDING",
  "transactionCode": "ORD11234567890",
  "createdAt": "2026-05-20T10:30:45.123456",
  "message": null,
  "failureReason": null
}
```

**Expected Response (COMPLETED - After Payment):**
```json
{
  "transactionId": 1,
  "orderId": 1,
  "amount": 1000000,
  "method": "Thanh toán VNPay",
  "status": "COMPLETED",
  "transactionCode": "ORD11234567890",
  "paidAt": "2026-05-20T10:32:15.654321",
  "createdAt": "2026-05-20T10:30:45.123456",
  "message": "Thanh toán thành công",
  "failureReason": null
}
```

**cURL Command:**
```bash
curl -X GET http://localhost:8080/api/payment/status/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

### 3. Cancel Payment (Refund)

**Postman Setup:**
```http
POST http://localhost:8080/api/payment/cancel/1 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Length: 0
```

**Expected Response:**
```json
{
  "status": "success",
  "message": "Payment cancelled and refunded"
}
```

**cURL Command:**
```bash
curl -X POST http://localhost:8080/api/payment/cancel/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Sandbox Testing Flow

### Complete End-to-End Test

**Step 1: Get JWT Token**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "buyer@example.com",
    "password": "password123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "email": "buyer@example.com",
  "role": "BUYER"
}
```

**Step 2: Add Items to Cart**
```bash
curl -X POST http://localhost:8080/api/cart/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "bookId": 1,
    "quantity": 2
  }'
```

**Step 3: Create Order**
```bash
curl -X POST http://localhost:8080/api/orders/me/checkout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "shippingAddress": "123 Main Street, District 1, Ho Chi Minh City"
  }'
```

Response:
```json
{
  "orderId": 1,
  "buyerId": 1,
  "shippingAddress": "123 Main Street, District 1, Ho Chi Minh City",
  "totalAmount": 1000000.0,
  "subOrderCount": 1
}
```

**Step 4: Initialize Payment**
```bash
curl -X POST http://localhost:8080/api/payment/vnpay/init \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "orderId": 1,
    "paymentMethod": "VNPAY",
    "returnUrl": "http://localhost:3000/checkout/payment-result"
  }'
```

Response with `paymentUrl` (copy this URL):
```json
{
  "transactionId": 1,
  "orderId": 1,
  "amount": 1000000,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Version=2.1.0&vnp_Command=pay&vnp_TmnCode=...",
  "transactionCode": "ORD11234567890",
  "message": "Payment URL generated successfully"
}
```

**Step 5: Open Payment URL in Browser**
1. Copy the `paymentUrl` from response
2. Paste into browser: `https://sandbox.vnpayment.vn/...`
3. Use test card: `4111111111111111`
4. Expiry: `12/25`
5. OTP: `123456`
6. Click "Thanh toán"

**Step 6: Verify Payment Completed**
```bash
curl -X GET http://localhost:8080/api/payment/status/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Should now show:
```json
{
  "status": "COMPLETED",
  "paidAt": "2026-05-20T10:35:00.000000",
  "message": "Thanh toán thành công"
}
```

---

## Database Verification

### Check Payment Transaction

```sql
SELECT * FROM payment_transactions WHERE order_id = 1;
```

Expected output:
```
id | order_id | amount   | method | status    | transaction_code | paid_at              | created_at
1  | 1        | 1000000  | VNPAY  | COMPLETED | ORD11234567890   | 2026-05-20 10:35:00 | 2026-05-20 10:30:45
```

### Check Order Status

```sql
SELECT o.id, o.buyer_id, o.total_amount, o.created_at
FROM orders_master o
WHERE o.id = 1;
```

---

## VNPay Sandbox Credentials

| Field | Value |
|-------|-------|
| Test Card | 4111111111111111 |
| Expiry | 12/25 (any future date) |
| CVV | Any 3 digits |
| OTP | 123456 |
| Response Code (Success) | 00 |
| Response Code (Failure) | 01-09 |

---

## Common Issues & Solutions

### Issue 1: "Invalid Secure Hash"
**Cause**: Secret key mismatch  
**Solution**: 
```properties
# Verify in application.properties
vnpay.hash-secret=YOUR_ACTUAL_SECRET_FROM_VNPAY
```

### Issue 2: "Payment Link Expired"
**Cause**: Link used after 15 minutes  
**Solution**: Create new payment link:
```bash
curl -X POST http://localhost:8080/api/payment/vnpay/init ...
```

### Issue 3: "Order Not Found"
**Cause**: Order doesn't exist  
**Solution**: Create order first via checkout endpoint

### Issue 4: "No JWT Token"
**Cause**: Missing Authorization header  
**Solution**: Include JWT:
```bash
-H "Authorization: Bearer YOUR_TOKEN"
```

---

## JavaScript Integration Example

```javascript
// Initialize VNPay Payment
async function initiateVNPayPayment(orderId) {
  try {
    const response = await fetch('/api/payment/vnpay/init', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getToken()}`
      },
      body: JSON.stringify({
        orderId: orderId,
        paymentMethod: 'VNPAY',
        returnUrl: `${window.location.origin}/checkout/payment-result`
      })
    });

    const data = await response.json();

    if (response.ok && data.paymentUrl) {
      // Redirect to VNPay
      window.location.href = data.paymentUrl;
    } else {
      alert('Error: ' + (data.message || 'Unknown error'));
    }
  } catch (error) {
    alert('Error: ' + error.message);
  }
}

// Check payment status
async function checkPaymentStatus(orderId) {
  try {
    const response = await fetch(`/api/payment/status/${orderId}`, {
      headers: {
        'Authorization': `Bearer ${getToken()}`
      }
    });

    const data = await response.json();
    console.log('Payment Status:', data);
    
    if (data.status === 'COMPLETED') {
      alert('✅ Payment successful!');
      // Redirect to order details
      window.location.href = `/main/order-details?orderId=${orderId}`;
    } else if (data.status === 'FAILED') {
      alert('❌ Payment failed: ' + (data.failureReason || 'Unknown error'));
    }
  } catch (error) {
    alert('Error: ' + error.message);
  }
}

// Get JWT Token from localStorage
function getToken() {
  return localStorage.getItem('jwt_token') || sessionStorage.getItem('jwt_token');
}
```

---

## Performance Tips

1. **Async Processing**: Payment verification is non-blocking
2. **Connection Pool**: HikariCP configured with 20 max connections
3. **Index Performance**: Payment queries indexed on `status` and `created_at`
4. **Timeout**: Payment links expire after 15 minutes to save storage

---

## Next: Production Deployment

1. Get live VNPay credentials from merchant portal
2. Update `vnpay.tmn-code` and `vnpay.hash-secret`
3. Change endpoints to production URLs
4. Enable HTTPS only
5. Set up monitoring & alerting for payment failures
