package com.example.bookstore.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.example.bookstore.model.Coupon;

import java.io.IOException;

/**
 * Custom deserializer for CouponType enum
 * Handles frontend variations and normalizes them to backend enum values
 * 
 * Supports:
 * - FIXED, PERCENT (correct backend values)
 * - FIXED_AMOUNT, PERCENTAGE (legacy frontend values - auto-converted)
 * 
 * This prevents JSON deserialization errors when frontend sends different
 * enum values than the backend expects.
 */
public class CouponTypeDeserializer extends JsonDeserializer<Coupon.CouponType> {
    
    @Override
    public Coupon.CouponType deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) 
            throws IOException {
        String value = jsonParser.getText();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("CouponType cannot be null or empty");
        }
        
        value = value.toUpperCase().trim();
        
        // Normalize frontend values to backend enum values
        return switch (value) {
            // Correct backend values
            case "FIXED" -> Coupon.CouponType.FIXED;
            case "PERCENT" -> Coupon.CouponType.PERCENT;
            
            // Legacy/alternative frontend values (auto-convert)
            case "FIXED_AMOUNT" -> Coupon.CouponType.FIXED;
            case "PERCENTAGE" -> Coupon.CouponType.PERCENT;
            
            default -> throw new IllegalArgumentException(
                "Invalid CouponType: " + value + ". Allowed values: FIXED, PERCENT, FIXED_AMOUNT, PERCENTAGE"
            );
        };
    }
}
