package com.example.bookstore.service;

import com.example.bookstore.dto.UserProfileResponse;
import com.example.bookstore.dto.UserProfileUpdateRequest;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.User;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.service.cluster.CustomerAnalysisService;
import com.example.bookstore.service.cluster.CustomerService;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service // BẮT BUỘC PHẢI CÓ để Lễ tân AuthController gọi được
public class AuthService {

    @Autowired
    private UserRepository userRepository; //Nhờ lính đánh thuê để tìm dữ liệu

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AuthOtpService authOtpService;

    @Autowired
    private SellerShopRepository sellerShopRepository;

    @Autowired
    private SellerShopService sellerShopService;

    @Autowired
    private CustomerAnalysisService customerAnalysisService;

    @Autowired
    private CustomerService customerService;

    /**
     * Submit a seller application: create a SellerShop record with PENDING approval.
     * Does NOT change the user's role — admin must approve to set role to SELLER.
     */
    public SellerShop submitSellerApplication(Long userId, String shopName, String shopAddress, String city, String province) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Prevent duplicate applications / existing shop
        if (sellerShopRepository.findBySellerId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã gửi yêu cầu hoặc đã có cửa hàng.");
        }

        String slug = sellerShopService.generateUniqueSlug(user.getUsername());
        com.example.bookstore.model.SellerShop newShop = com.example.bookstore.model.SellerShop.builder()
                .seller(user)
                .slug(slug)
                .shopName(shopName != null && !shopName.isBlank() ? shopName.trim() : user.getUsername())
                .address(shopAddress != null && !shopAddress.isBlank() ? shopAddress.trim() : "Chưa cập nhật")
                .city(city != null && !city.isBlank() ? city.trim() : "Chưa cập nhật")
                .province(province != null && !province.isBlank() ? province.trim() : null)
                .approvalStatus(ApprovalStatus.PENDING)
                .build();

        com.example.bookstore.model.SellerShop saved = sellerShopRepository.save(newShop);
        return saved;
    }

    //    Register
    public boolean register(String username, String rawPassword, String avatarUrl, List<Long> favoriteCategoryIds){
        return registerWithRole(username, rawPassword, avatarUrl, favoriteCategoryIds, UserRole.BUYER);
    }

    public boolean registerWithRole(
        String username,
        String rawPassword,
        String avatarUrl,
        List<Long> favoriteCategoryIds,
        UserRole role
    ) {
        UserRole normalizedRole = (role == null) ? UserRole.BUYER : role;
        if (normalizedRole != UserRole.BUYER && normalizedRole != UserRole.SELLER && normalizedRole != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role khong hop le");
        }

        if (!authOtpService.consumeVerifiedEmail(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chua xac thuc OTP");
        }

        if (userRepository.existsByUsername(username)) {
            System.out.println("Tên đăng nhập đã tồn tại");
            return false;
        }

        //Băm mật khẩu với độ khó (work factor) là 12 để chống brute-force
        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        User newUser = User.builder()
            .username(username)
            .passwordHash(hashedPassword)
            .role(normalizedRole)
            .avatarUrl(normalizeAvatar(avatarUrl))
            .favoriteCategories(resolveFavoriteCategories(favoriteCategoryIds))
            .build();
        User savedUser = userRepository.save(newUser);

        // Create SellerShop automatically for new SELLER registrations
        if (normalizedRole == UserRole.SELLER) {
            String slug = sellerShopService.generateUniqueSlug(username);
            SellerShop newSellerShop = SellerShop.builder()
                    .seller(savedUser)
                    .slug(slug)
                    .shopName(username)
                    .address("Chưa cập nhật")
                    .city("Chưa cập nhật")
                    .approvalStatus(ApprovalStatus.PENDING)
                    .build();
            sellerShopRepository.save(newSellerShop);
        }

        // Tự động phân tích churn cho user mới (gọi Python ML API)
        try {
            // analyzeCustomer() tự tạo Customer record nếu chưa tồn tại,
            // tính features từ dữ liệu thực tế, gọi ML API và lưu kết quả
            customerAnalysisService.analyzeCustomer(savedUser.getId());
            log.info("Đã phân tích churn cho user mới: {}", savedUser.getId());
        } catch (Exception e) {
            log.error("Không thể phân tích churn cho user {}: {}", savedUser.getId(), e.getMessage());
        }

        System.out.println("Đăng ký thành công");
        return true;
    }

//    Login
    public boolean login (String username, String rawPassword){
        return authenticateUser(username, rawPassword) != null;
    }

    public User authenticateUser(String username, String rawPassword) {
        User user = userRepository.findByUsername(username);

        if(user == null){
            System.out.println("Lỗi đăng nhập");
            return null;
        }
//        Kiểm tra có khớp với băm hay không
        if(BCrypt.checkpw(rawPassword, user.getPasswordHash())){
            System.out.println("Đăng nhập thành công");
            return user;
        }
        else {
            System.out.println("Sai thông tin đăng nhập");
            return null;
        }
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return toUserProfileResponse(user);
    }

    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            User existing = userRepository.findByUsername(request.getUsername());
            if (existing != null && !existing.getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
            }
            user.setUsername(request.getUsername().trim());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(normalizeAvatar(request.getAvatarUrl()));
        }

        if (request.getFavoriteCategoryIds() != null) {
            user.setFavoriteCategories(resolveFavoriteCategories(request.getFavoriteCategoryIds()));
        }

        // Shop info is only meaningful for seller/admin storefront management.
        if (user.getRole() == UserRole.SELLER || user.getRole() == UserRole.ADMIN) {
            if (request.getShopName() != null) {
                user.setShopName(request.getShopName().trim());
            }
            if (request.getShopAddress() != null) {
                user.setShopAddress(request.getShopAddress().trim());
            }
        }

        userRepository.save(user);
        return toUserProfileResponse(user);
    }

    /**
     * Upgrade a BUYER user to SELLER with optional shop info.
     */
    public User upgradeToSeller(Long userId, String shopName, String shopAddress) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getRole() == UserRole.SELLER) {
            return user; // already a seller
        }

        if (user.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only buyers can be upgraded to seller");
        }

        user.setRole(UserRole.SELLER);
        if (shopName != null && !shopName.isBlank()) user.setShopName(shopName.trim());
        if (shopAddress != null && !shopAddress.isBlank()) user.setShopAddress(shopAddress.trim());

        userRepository.save(user);
        return user;
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        return UserProfileResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .role(user.getRole())
            .shopName(user.getShopName())
            .shopAddress(user.getShopAddress())
            .avatarUrl(user.getAvatarUrl())
            .favoriteCategoryIds(user.getFavoriteCategories().stream().map(Category::getId).toList())
            .build();
    }

    private Set<Category> resolveFavoriteCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Long> normalizedIds = categoryIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .collect(Collectors.toList());

        if (normalizedIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Category> categories = categoryRepository.findAllById(normalizedIds);
        if (categories.size() != normalizedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Co the loai yeu thich khong ton tai");
        }

        return new LinkedHashSet<>(categories);
    }

    private String normalizeAvatar(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }
        String trimmed = avatarUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
