package com.example.bookstore.controller;

import com.example.bookstore.config.PythonApiConfig;
import com.example.bookstore.dto.ml.CustomerMLInput;
import com.example.bookstore.dto.ml.PredictionResult;
import com.example.bookstore.service.cluster.MlApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Controller công khai cho frontend gọi:
 * - Dự đoán churn & phân cụm khách hàng
 * - Kiểm tra kết nối Python ML API
 */
@Slf4j
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerPredictionController {

    private final MlApiService mlApiService;
    private final PythonApiConfig pythonApiConfig;
    private final RestTemplate restTemplate;

    /**
     * Endpoint cho frontend gọi: nhận raw data → gửi Python → trả kết quả
     * POST /api/customer/predict
     */
    @PostMapping("/predict")
    public ResponseEntity<PredictionResult> predict(@RequestBody CustomerMLInput request) {
        log.info("Received predict request: {}", request);
        PredictionResult result = mlApiService.predictCustomer(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Kiểm tra Python API còn sống không
     * GET /api/customer/python-health
     */
    @GetMapping("/python-health")
    public ResponseEntity<String> checkPythonHealth() {
        try {
            String response = restTemplate.getForObject(
                pythonApiConfig.getHealthUrl(),
                String.class
            );
            if (response != null && response.contains("hoạt động")) {
                return ResponseEntity.ok("Python AI Microservice is connected!");
            }
            return ResponseEntity.ok("Python AI responded: " + response);
        } catch (Exception e) {
            log.error("Python API health check failed: {}", e.getMessage());
            return ResponseEntity.status(503)
                .body("Python AI Microservice is not reachable: " + e.getMessage());
        }
    }
}
