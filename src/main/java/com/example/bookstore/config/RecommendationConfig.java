package com.example.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationConfig {

    // FP-Growth Configurations
    private double minSupport = 0.01;      // Minimum 1% transactions must contain the itemset
    private double minConfidence = 0.1;    // Minimum 10% confidence for the rule
    private double minLift = 1.0;          // Rule must lift likelihood above random chance
    
    // Similarity Configurations
    private double authorWeight = 0.4;     // Weight for same author
    private double categoryWeight = 0.4;   // Weight for same category
    private double textWeight = 0.2;       // Weight for title/description TF-IDF

    // Display Limits
    private int maxBoughtTogether = 10;
    private int maxSimilar = 10;

    // Getters and Setters

    public double getMinSupport() { return minSupport; }
    public void setMinSupport(double minSupport) { this.minSupport = minSupport; }

    public double getMinConfidence() { return minConfidence; }
    public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }

    public double getMinLift() { return minLift; }
    public void setMinLift(double minLift) { this.minLift = minLift; }

    public double getAuthorWeight() { return authorWeight; }
    public void setAuthorWeight(double authorWeight) { this.authorWeight = authorWeight; }

    public double getCategoryWeight() { return categoryWeight; }
    public void setCategoryWeight(double categoryWeight) { this.categoryWeight = categoryWeight; }

    public double getTextWeight() { return textWeight; }
    public void setTextWeight(double textWeight) { this.textWeight = textWeight; }

    public int getMaxBoughtTogether() { return maxBoughtTogether; }
    public void setMaxBoughtTogether(int maxBoughtTogether) { this.maxBoughtTogether = maxBoughtTogether; }

    public int getMaxSimilar() { return maxSimilar; }
    public void setMaxSimilar(int maxSimilar) { this.maxSimilar = maxSimilar; }
}
