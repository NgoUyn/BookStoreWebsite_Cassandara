package com.example.bookstore.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter cho cột loyalty_member.
 * Chuyển đổi giữa Double (0.0/1.0) trong Java và NVARCHAR ("No"/"Yes" hoặc số) trong SQL Server.
 * 
 * Hỗ trợ đọc:
 * - "No" → 0.0
 * - "Yes" → 1.0
 * - "0.0" → 0.0
 * - "1.0" → 1.0
 * - null → 0.0
 * 
 * Ghi:
 * - 0.0 → "No"
 * - 1.0 → "Yes"
 */
@Converter
public class LoyaltyMemberConverter implements AttributeConverter<Double, String> {

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        if (attribute == null) {
            return "No";
        }
        // 1.0 = Yes, mọi giá trị khác = No
        return Math.abs(attribute - 1.0) < 0.001 ? "Yes" : "No";
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return 0.0;
        }
        String trimmed = dbData.trim();
        // Xử lý "No" / "Yes"
        if ("Yes".equalsIgnoreCase(trimmed)) {
            return 1.0;
        }
        if ("No".equalsIgnoreCase(trimmed)) {
            return 0.0;
        }
        // Xử lý trường hợp đã là số (FLOAT)
        try {
            double val = Double.parseDouble(trimmed);
            return val;
        } catch (NumberFormatException e) {
            // Fallback: mọi giá trị không parse được đều là 0.0
            return 0.0;
        }
    }
}
