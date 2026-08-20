package com.example.bookstore.service.recommendation.fpgrowth;

/**
 * Lớp lưu trữ kết quả của luật kết hợp (A -> B) sau khi khai phá dữ liệu.
 * Giữ nguyên cấu trúc các thuộc tính để tương thích tốt với luồng lưu database hiện tại.
 */
public class AssociationRule {
    private final Long antecedent;  // ID của cuốn sách tiền đề (Sách A)
    private final Long consequent;  // ID của cuốn sách hệ quả (Sách B)
    private final double support;   // Do ho tro cua cap A-B 
    private final double confidence; // Độ tin cậy của luật
    private final double lift;       // Độ nâng của luật

    public AssociationRule(Long antecedent, Long consequent,double support, double confidence, double lift) {
        this.antecedent = antecedent;
        this.consequent = consequent;
        this.support = support;
        this.confidence = confidence;
        this.lift = lift;
    }

    public Long getAntecedent() { 
        return antecedent; 
    }

    public Long getConsequent() { 
        return consequent; 
    }


    public double getSupport() { return support; }
    public double getConfidence() { 
        return confidence; 
    }

    public double getLift() { 
        return lift; 
    }
}