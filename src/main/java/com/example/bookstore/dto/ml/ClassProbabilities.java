package com.example.bookstore.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO chứa xác suất chi tiết cho từng lớp rủi ro (3 lớp).
 * Được lồng bên trong PredictionResult.
 */
@Data
public class ClassProbabilities {
    @JsonProperty("low_risk")
    private Double lowRisk;

    @JsonProperty("medium_risk")
    private Double mediumRisk;

    @JsonProperty("high_risk")
    private Double highRisk;
}
