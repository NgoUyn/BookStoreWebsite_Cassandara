package com.example.bookstore.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO gửi lên Python ML API để dự đoán hành vi khách hàng (mô hình final-gauss-lightgbm).
 * Gồm 12 raw features bắt buộc, đồng bộ với features_config.json → required_raw_features.
 * Python sẽ tự động engineering thêm features.
 * <p>
 * @JsonPropertyOrder(alphabetic = true) đảm bảo thứ tự các trường khi serialize JSON
 * khớp với thứ tự alphabet của snake_case property names, trùng với thứ tự
 * trong docs/Cluster/features_config.json → required_raw_features.
 * Điều này giải quyết lỗi "Feature names must be in the same order as they were in fit"
 * từ phía Python ML API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder(alphabetic = true)
public class CustomerMLInput {
    @JsonProperty(value = "account_age_months", index = 1)
    private Double accountAgeMonths;

    @JsonProperty(value = "avg_order_value", index = 2)
    private Double avgOrderValue;

    @JsonProperty(value = "browsing_frequency_per_week", index = 3)
    private Double browsingFrequencyPerWeek;

    @JsonProperty(value = "cart_abandonment_rate", index = 4)
    private Double cartAbandonmentRate;

    @JsonProperty(value = "customer_support_tickets", index = 5)
    private Double customerSupportTickets;

    @JsonProperty(value = "discount_usage_rate", index = 6)
    private Double discountUsageRate;

    @JsonProperty(value = "loyalty_member", index = 7)
    private Double loyaltyMember;

    @JsonProperty(value = "price_sensitivity_index", index = 8)
    private Double priceSensitivityIndex;

    @JsonProperty(value = "product_review_score_avg", index = 9)
    private Double productReviewScoreAvg;

    @JsonProperty(value = "return_rate", index = 10)
    private Double returnRate;

    @JsonProperty(value = "satisfaction_score", index = 11)
    private Double satisfactionScore;

    @JsonProperty(value = "total_orders", index = 12)
    private Double totalOrders;

}
