package com.example.bookstore.controller;
import static org.mockito.ArgumentMatchers.*;
import com.example.bookstore.dto.CheckoutMeRequest;
import com.example.bookstore.dto.CheckoutResponse;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCheckoutWithCurrentBuyerContext() throws Exception {
        CheckoutMeRequest request = new CheckoutMeRequest();
        request.setShippingAddress("Q1, HCM");

        CheckoutResponse response = CheckoutResponse.builder()
            .orderId(101L)
            .buyerId(1L)
            .shippingAddress("Q1, HCM")
            .totalAmount(320000.0)
            .shippingFee(30000.0)
            .subOrderCount(2)
            .build();

        when(orderService.checkoutFromCurrentBuyer(any(), anyString(), any())).thenReturn(response);


        mockMvc.perform(post("/api/orders/me/checkout")
                .principal(new UsernamePasswordAuthenticationToken(
                    new JwtAuthenticatedPrincipal(1L, java.util.List.of("BUYER"), null),
                    null,
                    java.util.List.of()
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(101))
            .andExpect(jsonPath("$.subOrderCount").value(2));
    }

    @Test
    void shouldGetCurrentBuyerOrders() throws Exception {
        when(orderService.getCurrentBuyerOrders(any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/orders/me")
                .principal(new UsernamePasswordAuthenticationToken(
                    new JwtAuthenticatedPrincipal(1L, java.util.List.of("BUYER"), null),
                    null,
                    java.util.List.of()
                )))
            .andExpect(status().isOk());
    }
}
