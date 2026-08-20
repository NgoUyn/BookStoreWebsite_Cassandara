package com.example.bookstore.service.recommendation.fpgrowth;

public class HeaderTableItem {
    public Long bookId;
    
    // Tổng số lần bookId này xuất hiện trong toàn bộ các giao dịch
    public int totalCount; 
    
    // Trỏ tới FPNode đầu tiên chứa bookId này trên cây FP-Tree
    public FPNode head; 
    
    // Trỏ tới FPNode cuối cùng (dùng để chèn node mới vào cuối danh sách liên kết nhanh hơn O(1))
    public FPNode tail; 

    public HeaderTableItem(Long bookId, int totalCount) {
        this.bookId = bookId;
        this.totalCount = totalCount;
        this.head = null;
        this.tail = null;
    }

    public void addNode(FPNode node) {
        if (head == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
    }
}