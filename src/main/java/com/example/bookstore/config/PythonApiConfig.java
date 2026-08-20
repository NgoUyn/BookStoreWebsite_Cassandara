package com.example.bookstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình URL cho Python ML API.
 * Đọc từ application.properties: ml.api.url
 */
@Configuration
public class PythonApiConfig {

    @Value("${ml.api.url:http://localhost:8000/api/v1/predict}")
    private String predictUrl;

    @Value("${ml.api.base-url:http://localhost:8000}")
    private String baseUrl;

    public String getPredictUrl() {
        return predictUrl;
    }

    public String getHealthUrl() {
        return baseUrl + "/";
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
