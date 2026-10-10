package com.example.bookstore.model.enums;

public enum OrderStatus {
    PENDING,    // chờ người bán xác nhận
    PROCESSING, // đang xác nhận
    CONFIRMED,  // đã xác nhận
    SHIPPING,   // ĐANG GIAO
    DELIVERED,  // ĐÃ GIAO HÀNG
    COMPLETED,  // ĐÃ HOÀN THÀNH
    CANCELLED,  // ĐÃ HỦY
    RETURNED    // ĐÃ TRẢ HÀNG
}