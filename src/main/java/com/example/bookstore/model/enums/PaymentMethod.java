package com.example.bookstore.model.enums;

public enum PaymentMethod {
    COD("Cash on Delivery"),
    CREDIT_CARD("Thẻ tín dụng"),
    DEBIT_CARD("Thẻ ghi nợ"),
    MOMO("Ví MoMo"),
    BANK_TRANSFER("Chuyển khoản ngân hàng"),
    VNPAY("VNPay");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
