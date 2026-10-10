package com.example.bookstore.model.enums;

public enum NotificationType {
    ORDER_CREATED,
    ORDER_STATUS_CHANGED,
    ORDER_DELIVERED,
    ORDER_SHIPPING,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PROMOTION,
    SYSTEM,
    // Các loại thông báo do ứng dụng phát sinh
    SUB_ORDER_STATUS_CHANGED,
    SHOP_APPROVAL_UPDATED,
    BOOK_APPROVAL_UPDATED,
    SYSTEM_ANNOUNCEMENT,
    COUPON_CREATED
}
