# ⚡ VNPay Payment Quick Setup Guide

## 📋 What Was Created

| File | Purpose |
|------|---------|
| `PaymentTransaction.java` | JPA Entity to store payment records |
| `PaymentMethod.java` | Enum: COD, CREDIT_CARD, DEBIT_CARD, MOMO, BANK_TRANSFER, VNPAY |
| `PaymentStatus.java` | Enum: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REFUNDED |
| `PaymentTransactionRepository.java` | Database queries |
| `VnpayService.java` | Core payment logic (build URL, handle callback, cancel) |
| `PaymentController.java` | REST endpoints for payment operations |
| `PaymentInitRequest.java` | DTO: orderId, paymentMethod, returnUrl |
| `PaymentInitResponse.java` | DTO: transactionId, paymentUrl, transactionCode |
| `PaymentStatusResponse.java` | DTO: status, paidAt, failureReason |
| `V18__create_payment_transactions_table.sql` | Database migration |
| `VNPAY_INTEGRATION_GUIDE.md` | Full integration documentation |

## 🚀 Quick Start (3 Steps)

### Step 1: Update Configuration
Edit `application.properties`:
```properties
vnpay.tmn-code=YOUR_TMN_CODE
vnpay.hash-secret=YOUR_HASH_SECRET
vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.return-url=http://localhost:8080/api/payment/vnpay-callback
```

### Step 2: Run Database Migration
```bash
mvn flyway:migrate
```

This creates `payment_transactions` table automatically.

### Step 3: Update Frontend
Call payment API from checkout page:
```javascript
// When user clicks "Place Order"
const paymentResponse = await fetch('/api/payment/vnpay/init', {
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

const data = await paymentResponse.json();
window.location.href = data.paymentUrl; // Redirect to VNPay
```

## 📡 API Endpoints

| Method | Endpoint | Purpose | Auth |
|--------|----------|---------|------|
| POST | `/api/payment/vnpay/init` | Create payment link | ✅ Required |
| GET | `/api/payment/vnpay-callback` | VNPay redirect | ❌ Public |
| GET | `/api/payment/status/{orderId}` | Check payment status | ✅ Required |
| POST | `/api/payment/cancel/{orderId}` | Refund payment | ✅ Required |

## 🔄 Payment Flow

```
1. User places order
   ↓
2. Frontend calls POST /api/payment/vnpay/init
   ↓
3. Backend creates PaymentTransaction (PENDING status)
   ↓
4. Backend generates VNPay URL with HMAC SHA-512 signature
   ↓
5. Frontend redirects to VNPay payment page
   ↓
6. Customer enters card details and pays
   ↓
7. VNPay redirects to GET /api/payment/vnpay-callback
   ↓
8. Backend verifies signature and updates PaymentTransaction
   ↓
9. If payment successful:
   - PaymentTransaction status = COMPLETED
   - Order status = PROCESSING (ready for shipping)
   ↓
10. Frontend shows success page or redirects
```

## 💾 Database Schema

```sql
payment_transactions (
  id BIGINT (PK),
  order_id BIGINT (FK),
  amount BIGINT,
  method NVARCHAR(30),     -- 'VNPAY', 'COD', etc.
  status NVARCHAR(30),     -- 'PENDING', 'COMPLETED', 'FAILED', etc.
  transaction_code NVARCHAR(100),  -- Unique: 'ORD123456789'
  payment_url NVARCHAR(1000),      -- VNPay redirect URL
  response_code NVARCHAR(1000),    -- VNPay response: '00', '01', etc.
  response_message NVARCHAR(1000), -- VNPay message
  created_at DATETIME2,
  paid_at DATETIME2,
  expired_at DATETIME2,
  failure_reason NVARCHAR(500)
)
```

## 🧪 Testing

### Sandbox Credentials
- **Endpoint**: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
- **Test Card**: 4111111111111111
- **Expiry**: 12/25
- **OTP**: 123456
- **Success Code**: 00

### Local Testing
```bash
# 1. Start application
mvn spring-boot:run

# 2. In Postman, POST to:
POST http://localhost:8080/api/payment/vnpay/init
Headers: Authorization: Bearer {jwt_token}
Body:
{
  "orderId": 1,
  "paymentMethod": "VNPAY",
  "returnUrl": "http://localhost:3000/payment-result"
}

# 3. Response will contain paymentUrl
# 4. Copy/paste paymentUrl into browser
# 5. Use sandbox test card to complete payment
```

## ⚙️ Configuration Details

### application.properties
```properties
# VNPay - Get these from merchant.vnpayment.vn
vnpay.tmn-code=YOUR_MERCHANT_CODE
vnpay.hash-secret=YOUR_SECRET_KEY
vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.return-url=http://localhost:8080/api/payment/vnpay-callback

# Production use:
# vnpay.pay-url=https://payment.vnpayment.vn/paymentv2/vpcpay.html
# vnpay.return-url=https://yourdomain.com/api/payment/vnpay-callback
```

## 🔐 Security Notes

1. **HMAC SHA-512 Signing**: All requests/responses signed with secret key
2. **Payment Link Expiry**: 15 minutes by default
3. **Idempotency**: Duplicate callbacks handled gracefully
4. **HTTPS Only**: Use HTTPS in production
5. **No Card Storage**: Card details never stored locally

## 📊 Payment Status Flow

```
CREATE ORDER (PENDING_PAYMENT)
           ↓
    INITIATE PAYMENT
           ↓
    PENDING (waiting for callback)
           ↓
    ┌─────────┴──────────┐
    ↓                    ↓
 COMPLETED         FAILED
    ↓                    ↓
PROCESSING        ← RETRY
    ↓
SHIPPED
    ↓
COMPLETED
```

## ❌ Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| 00 | Success | Update status to COMPLETED |
| 01-09 | Payment declined | Show error, allow retry |
| 97 | Invalid signature | Check secret key in config |
| 99 | System error | Try again later |

## 🛠️ Troubleshooting

### "Invalid signature" Error
- Check `vnpay.hash-secret` matches VNPay admin panel
- Ensure `VnpayUtil.hashAllFields()` is calculating correctly
- Verify all parameters are included in signature

### "Payment link expired"
- Payment links valid for 15 minutes
- Create new transaction if expired
- Check `expired_at` timestamp in database

### No Callback Received
- Verify `vnpay.return-url` is correct
- Check firewall/network allows VNPay IP
- Enable HTTPS if in production
- Check application logs for errors

## 📚 Files to Integrate Frontend

1. Update `Checkout_Page.html` - Add VNPay radio button
2. Update `checkout-page.js` - Add VNPay payment handler
3. Create `Payment_Result.html` - Show payment result
4. Update `api-service.js` - Add payment API methods

## 🎯 Next: Frontend Implementation

See **VNPAY_INTEGRATION_GUIDE.md** for:
- HTML form setup
- JavaScript integration code
- Payment result page template
- Error handling examples
