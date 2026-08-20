package com.example.bookstore.service;

import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.dto.SellerShopUpsertRequest;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerShopServiceTest {

    @Mock
    private SellerShopRepository shopRepository;

    @Mock
    private UserRepository userRepository;

    private SellerShopService sellerShopService;

    @BeforeEach
    void setUp() {
        sellerShopService = new SellerShopService(shopRepository, userRepository);
    }

    @Test
    void createMyShop_shouldCreateSuccessfully() {
        Long sellerId = 1L;

        User seller = User.builder()
            .id(sellerId)
            .username("seller")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();

        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("Nha Nam Official")
            .slug("nha-nam-official")
            .description("Sach chat luong")
            .contactEmail("shop@example.com")
            .build();

        SellerShop saved = SellerShop.builder()
            .id(10L)
            .seller(seller)
            .shopName(request.getShopName())
            .slug(request.getSlug())
            .description(request.getDescription())
            .contactEmail(request.getContactEmail())
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(shopRepository.findBySellerId(sellerId)).thenReturn(Optional.empty());
        when(shopRepository.existsBySlug(request.getSlug())).thenReturn(false);
        when(shopRepository.save(any(SellerShop.class))).thenReturn(saved);

        SellerShopResponse response = sellerShopService.createMyShop(sellerId, request);

        assertEquals(10L, response.getId());
        assertEquals("nha-nam-official", response.getSlug());
        assertEquals("Nha Nam Official", response.getShopName());
        assertEquals(ApprovalStatus.PENDING, response.getApprovalStatus());
    }

    @Test
    void createMyShop_shouldRejectDuplicateSlug() {
        Long sellerId = 1L;

        User seller = User.builder()
            .id(sellerId)
            .username("seller")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();

        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("Shop A")
            .slug("shop-a")
            .build();

        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(shopRepository.findBySellerId(sellerId)).thenReturn(Optional.empty());
        when(shopRepository.existsBySlug("shop-a")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sellerShopService.createMyShop(sellerId, request));

        assertEquals("Đường dẫn (Slug) này đã tồn tại. Vui lòng chọn đường dẫn khác.", ex.getMessage());
    }

    @Test
    void createMyShop_shouldRejectNonSellerRole() {
        Long userId = 2L;

        User buyer = User.builder()
            .id(userId)
            .username("buyer")
            .passwordHash("x")
            .role(UserRole.BUYER)
            .build();

        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("Shop B")
            .slug("shop-b")
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(buyer));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sellerShopService.createMyShop(userId, request));

        assertEquals("Bạn không có quyền đăng ký cửa hàng (Yêu cầu Role SELLER).", ex.getMessage());
    }

    @Test
    void updateMyShop_shouldRejectWhenSellerDoesNotOwnShop() {
        Long sellerId = 88L;

        SellerShopUpsertRequest request = SellerShopUpsertRequest.builder()
            .shopName("New Name")
            .slug("new-slug")
            .build();

        when(shopRepository.findBySellerId(sellerId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sellerShopService.updateMyShop(sellerId, request));

        assertEquals("Không tìm thấy cửa hàng của bạn.", ex.getMessage());
    }

    @Test
    void changeStatus_shouldRejectSelfApproval() {
        Long sellerId = 1L;

        User seller = User.builder()
            .id(sellerId)
            .username("seller")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();

        SellerShop shop = SellerShop.builder()
            .id(99L)
            .seller(seller)
            .slug("seller-shop")
            .shopName("Seller Shop")
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(shopRepository.findBySellerId(sellerId)).thenReturn(Optional.of(shop));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sellerShopService.changeStatus(sellerId, ApprovalStatus.APPROVED));

        assertEquals("Chỉ admin mới có quyền duyệt cửa hàng.", ex.getMessage());
    }
}
