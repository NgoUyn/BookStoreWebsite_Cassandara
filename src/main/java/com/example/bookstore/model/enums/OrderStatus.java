package com.example.bookstore.model.enums;

public enum OrderStatus {
    PROCESSING, // Đang xác nhận
    COMFIRMED,  // Đã xác nhận (giữ tên cũ để tương thích)
    CONFIRMED,  // Tên viết đúng
    SHIPPING,   // Đang giao hàng
    DELIVERED,  // Đã giao hàng
    COMPLETED,  // Đã hoàn thành
    CANCELLED,  // Đã hủy
    RETURNED    // Đã trả hàng
}