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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            .sellerId(seller.getId())
            .seller(com.example.bookstore.model.embedded.UserSnapshot.of(seller))
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
            .sellerId(seller.getId())
            .seller(com.example.bookstore.model.embedded.UserSnapshot.of(seller))
            .slug("seller-shop")
            .shopName("Seller Shop")
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(shopRepository.findBySellerId(sellerId)).thenReturn(Optional.of(shop));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> sellerShopService.changeStatus(sellerId, ApprovalStatus.APPROVED));

        assertEquals("Chỉ admin mới có quyền duyệt cửa hàng.", ex.getMessage());
    }

    /**
     * Regression: hồ sơ legacy (shop _id=1 "Nhà xuất bản Trẻ") chỉ có sellerId ở cấp
     * document, snapshot thiếu seller.id -> trước đây approve ném IllegalArgumentException
     * (HTTP 500) vì userRepository.findById(null).
     */
    @Test
    void adminApproveSellerByShop_shouldResolveSellerIdFromTopLevelField_whenSnapshotIdMissing() {
        Long shopId = 1L;
        Long sellerId = 1510L;

        User seller = User.builder()
            .id(sellerId)
            .username("nhaxuatbantre@bookom.vn")
            .passwordHash("x")
            .role(UserRole.SELLER)
            .build();

        // Snapshot kiểu dữ liệu cũ: có thông tin nhưng THIẾU id
        com.example.bookstore.model.embedded.UserSnapshot legacySnapshot =
            com.example.bookstore.model.embedded.UserSnapshot.builder()
                .username("nhaxuatbantre@bookom.vn")
                .email("nhaxuatbantre@bookom.vn")
                .fullName("Nhà xuất bản Trẻ")
                .build();

        SellerShop legacyShop = SellerShop.builder()
            .id(shopId)
            .sellerId(sellerId) // field top-level vẫn còn
            .seller(legacySnapshot)
            .slug("nha-xuat-ban-tre")
            .shopName("Nhà xuất bản Trẻ")
            .approvalStatus(ApprovalStatus.REJECTED)
            .build();

        final SellerShop[] saved = new SellerShop[1];
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(legacyShop));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(shopRepository.save(any(SellerShop.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });

        SellerShopResponse response = sellerShopService.adminApproveSellerByShop(shopId);

        assertEquals(sellerId, response.getSellerId());
        assertEquals(ApprovalStatus.APPROVED, response.getApprovalStatus());
        // snapshot được backfill id để các luồng sau (mail/notification) không còn null
        assertEquals(sellerId, saved[0].getSeller().getId());
    }

    @Test
    void adminApproveSellerByShop_shouldReturn409_whenSellerIdCannotBeResolved() {
        Long shopId = 7L;

        SellerShop orphanShop = SellerShop.builder()
            .id(shopId)
            .slug("orphan-shop")
            .shopName("Orphan Shop")
            .approvalStatus(ApprovalStatus.PENDING)
            .build();

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(orphanShop));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> sellerShopService.adminApproveSellerByShop(shopId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void adminApproveSellerByShop_shouldReturn404_whenShopNotFound() {
        when(shopRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> sellerShopService.adminApproveSellerByShop(404L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void adminApproveSeller_shouldReturn400_whenSellerIdIsNull() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> sellerShopService.adminApproveSeller(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
