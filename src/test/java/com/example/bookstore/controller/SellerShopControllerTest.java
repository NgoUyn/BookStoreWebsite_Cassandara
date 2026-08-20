package com.example.bookstore.controller;

import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.dto.SellerShopUpsertRequest;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.SellerShopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
class SellerShopControllerTest {

    @Mock
    private SellerShopService sellerShopService;

    @Mock
    private BookRepository bookRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SellerShopController controller = new SellerShopController(sellerShopService, bookRepository);

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(new GlobalValidationExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createMyShop_shouldReturnCreated() throws Exception {
        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("Nha Nam Official")
            .slug("nha-nam-official")
            .description("Shop sach")
            .build();

        SellerShopResponse response = SellerShopResponse.builder()
            .id(100L)
            .sellerId(1L)
            .shopName("Nha Nam Official")
            .slug("nha-nam-official")
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(sellerShopService.createMyShop(eq(1L), any(SellerShopUpsertRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/seller/me/shop")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(new UsernamePasswordAuthenticationToken(
                    new JwtAuthenticatedPrincipal(1L, java.util.List.of("SELLER"), 1L),
                    null
                ))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.slug").value("nha-nam-official"));
    }

    @Test
    void createMyShop_shouldReturnBadRequestWhenMissingRequiredFields() throws Exception {
        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("")
            .slug("")
            .build();

        mockMvc.perform(post("/api/seller/me/shop")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(new UsernamePasswordAuthenticationToken(
                    new JwtAuthenticatedPrincipal(1L, java.util.List.of("SELLER"), 1L),
                    null
                ))
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicShopBySlug_shouldReturnOk() throws Exception {
        SellerShopResponse response = SellerShopResponse.builder()
            .id(200L)
            .shopName("Public Shop")
            .slug("public-shop")
            .approvalStatus(ApprovalStatus.APPROVED)
            .build();

        when(sellerShopService.getPublicShopBySlug("public-shop")).thenReturn(response);

        mockMvc.perform(get("/api/shops/public-shop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopName").value("Public Shop"));
    }

    @Test
    void changeStatus_shouldReturnOk() throws Exception {
        SellerShopResponse response = SellerShopResponse.builder()
            .id(300L)
            .sellerId(1L)
            .shopName("S1")
            .slug("s1")
            .approvalStatus(ApprovalStatus.APPROVED)
            .build();

        when(sellerShopService.changeStatus(1L, ApprovalStatus.APPROVED)).thenReturn(response);

        mockMvc.perform(patch("/api/seller/me/shop/status")
                .param("status", "APPROVED")
                .principal(new UsernamePasswordAuthenticationToken(
                    new JwtAuthenticatedPrincipal(1L, java.util.List.of("SELLER"), 1L),
                    null
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }
}
