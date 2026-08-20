package com.example.bookstore.service.recommendation.fpgrowth;

import java.util.HashMap;
import java.util.Map;

public class FPNode {
    public Long bookId;
    public int count;
    public FPNode parent;
    public Map<Long, FPNode> children;
    public FPNode next; // Con to lien ket cac node cung Bookid tren toan cay (FP-Tree)
    


    public FPNode(Long bookId, FPNode parent) {
        this.bookId = bookId;
        this.count = 0;
        this.parent = parent;
        this.children = new HashMap<>();
        this.next = null;
    }

   public void increment(int amount) {
        this.count += amount;
    }
}