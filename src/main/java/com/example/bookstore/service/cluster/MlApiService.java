package com.example.bookstore.service.cluster;

import com.example.bookstore.dto.ml.CustomerMLInput;
import com.example.bookstore.dto.ml.PredictionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service gọi HTTP đến Python ML API để dự đoán cluster & churn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ml.api.url}")
    private String mlApiUrl;

    /**
     * Gửi thông tin khách hàng lên ML API và nhận kết quả dự đoán.
     *
     * @param input thông tin khách hàng cần phân tích
     * @return kết quả dự đoán (clusterId, churnProbability, ...)
     */
    public PredictionResult predictCustomer(CustomerMLInput input) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<CustomerMLInput> request = new HttpEntity<>(input, headers);

        // ================================================================
        // DEBUG: Serialize request body để log ra devtool (qua error response)
        // ================================================================
        String jsonBody = null;
        try {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            jsonBody = objectMapper.writeValueAsString(input);
            log.info("========== 🐍 DEBUG: JSON gửi lên Python ML API ==========");
            log.info("URL: {}", mlApiUrl);
            log.info("Request Body:\n{}", jsonBody);
            log.info("==========================================================");
        } catch (JsonProcessingException e) {
            log.warn("Không thể serialize input thành JSON để debug: {}", e.getMessage());
        }

        log.info("Calling ML API at {} with input: {}", mlApiUrl, input);
        try {
            PredictionResult result = restTemplate.postForObject(mlApiUrl, request, PredictionResult.class);
            log.info("========== 🐍 DEBUG: Response từ Python ML API ==========");
            log.info("Response: {}", result);
            log.info("==========================================================");
            return result;
        } catch (Exception e) {
            log.error("========== 🐍 DEBUG: LỖI GỌI PYTHON ML API ==========");
            log.error("URL: {}", mlApiUrl);
            log.error("Input gửi lên: {}", input);
            log.error("JSON Body gửi lên:\n{}", jsonBody != null ? jsonBody : "N/A");
            // Log full stacktrace
            log.error("Full stacktrace:", e);
            log.error("==========================================================");
            // Ném exception kèm JSON body để frontend có thể log ra devtool
            throw new RuntimeException(
                "ML API call failed. Request body sent to Python:\n" +
                (jsonBody != null ? jsonBody : "N/A") +
                "\n\nPython error: " + e.getMessage(),
                e
            );
        }
    }
}
