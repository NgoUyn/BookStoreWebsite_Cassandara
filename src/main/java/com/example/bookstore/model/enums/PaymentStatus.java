package com.example.bookstore.model.enums;

public enum PaymentStatus {
    PENDING("Chờ thanh toán"),
    SUCCESS("Thanh toán thành công"),
    FAILED("Thanh toán thất bại"),
    CANCELLED("Hủy thanh toán"),
    EXPIRED("Hết hạn thanh toán"),
    REFUNDED("Đã hoàn tiền");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
