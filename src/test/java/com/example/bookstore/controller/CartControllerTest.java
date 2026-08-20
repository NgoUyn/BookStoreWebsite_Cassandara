package com.example.bookstore.controller;

import com.example.bookstore.dto.CartItemUpsertRequest;
import com.example.bookstore.dto.CartResponse;
import com.example.bookstore.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CartController controller = new CartController(cartService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldGetBuyerCart() throws Exception {
        CartResponse response = CartResponse.builder()
            .cartId(10L)
            .buyerId(1L)
            .totalItems(2)
            .totalAmount(150000.0)
            .build();

        when(cartService.getBuyerCart(1L)).thenReturn(response);

        mockMvc.perform(get("/api/carts/buyer/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cartId").value(10))
            .andExpect(jsonPath("$.buyerId").value(1))
            .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void shouldAddItemToCart() throws Exception {
        CartResponse response = CartResponse.builder()
            .cartId(10L)
            .buyerId(1L)
            .totalItems(3)
            .totalAmount(220000.0)
            .build();

        when(cartService.addItem(eq(1L), any(CartItemUpsertRequest.class))).thenReturn(response);

        CartItemUpsertRequest request = new CartItemUpsertRequest();
        request.setBookId(5L);
        request.setQuantity(2);

        mockMvc.perform(post("/api/carts/buyer/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3));

        ArgumentCaptor<CartItemUpsertRequest> captor = ArgumentCaptor.forClass(CartItemUpsertRequest.class);
        verify(cartService).addItem(eq(1L), captor.capture());
        assertEquals(5L, captor.getValue().getBookId());
        assertEquals(2, captor.getValue().getQuantity());
    }
}
