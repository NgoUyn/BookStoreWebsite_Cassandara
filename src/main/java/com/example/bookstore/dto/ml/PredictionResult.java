package com.example.bookstore.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO nhận kết quả dự đoán từ Python ML API (mô hình final-gauss-lightgbm).
 * Response thực tế từ Python API:
 * {
 *   "churn_probability": 0.05,
 *   "predicted_label": 0,
 *   "risk_level": "LOW"
 * }
 * 
 * predicted_label: 0 = Stay (Ở lại), 1 = Churn (Rời bỏ)
 * risk_level: "LOW" hoặc "HIGH"
 */
@Data
public class PredictionResult {
    @JsonProperty("churn_probability")
    private Double churnProbability; // Xác suất rời bỏ (0.0 - 1.0)

    @JsonProperty("predicted_label")
    private Integer predictedLabel;  // 0 = Stay, 1 = Churn

    @JsonProperty("risk_level")
    private String riskLevel;        // "LOW" hoặc "HIGH"
}
