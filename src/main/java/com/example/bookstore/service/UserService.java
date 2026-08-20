package com.example.bookstore.service;

import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerShopRepository sellerShopRepository;
    
    /**
     * Tìm người dùng theo username
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username));
    }

    /**
     * Lấy người dùng theo ID
     */
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
    
    /**
     * Lấy danh sách người dùng theo role
     */
    public List<User> getUsersByRole(UserRole role) {
        return userRepository.findAllByRole(role);
    }
    
    /**
     * Khóa tài khoản người dùng (Admin action)
     */
    public User lockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại: " + userId));
        user.setActive(false);
        return userRepository.save(user);
    }
    
    /**
     * Mở khóa tài khoản người dùng (Admin action)
     */
    public User unlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại: " + userId));
        user.setActive(true);
        return userRepository.save(user);
    }
    
    /**
     * Lấy tất cả người dùng
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * Lấy người dùng theo trạng thái active
     */
    public List<User> getUsersByActiveStatus(boolean isActive) {
        return userRepository.findByIsActive(isActive);
    }
    
    /**
     * Lấy người dùng theo role và trạng thái active
     */
    public List<User> getUsersByRoleAndStatus(UserRole role, boolean isActive) {
        return userRepository.findByRoleAndIsActive(role, isActive);
    }

    /**
     * Duyệt người dùng từ BUYER lên SELLER.
     * <p>
     * Sau khi set role mới cho User, tự động tạo một bản ghi SellerShop
     * với id của Shop trùng khít với id của User đó (Shared Primary Key pattern
     * sử dụng @MapsId) và lưu xuống database.
     * </p>
     *
     * @param userId ID của người dùng cần duyệt lên SELLER
     * @return User đã được cập nhật role
     * @throws RuntimeException nếu user không tồn tại, không phải BUYER,
     *                          hoặc đã có shop trước đó
     */
    @Transactional
    public User approveSeller(Long userId) {
        // 1. Tìm User theo ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại: " + userId));

        // 2. Kiểm tra User hiện tại phải là BUYER
        if (user.getRole() != UserRole.BUYER) {
            throw new RuntimeException(
                "Chỉ có thể duyệt user có role BUYER lên SELLER. " +
                "User hiện tại có role: " + user.getRole()
            );
        }

        // 3. Kiểm tra xem User đã có shop chưa (tránh tạo trùng)
        if (sellerShopRepository.findBySellerId(userId).isPresent()) {
            throw new RuntimeException("User này đã có shop, không thể tạo lại.");
        }

        // 4. Set role mới cho User
        user.setRole(UserRole.SELLER);
        userRepository.save(user);

        // 5. Tự động tạo SellerShop với id trùng khít với userId
        //    Nhờ @MapsId, Hibernate sẽ tự động gán SellerShop.id = seller.id
        SellerShop shop = SellerShop.builder()
                .seller(user)
                .slug("shop-" + userId) // Slug mặc định, user có thể đổi sau
                .shopName("Cửa hàng của " + user.getUsername())
                .address(user.getShopAddress() != null ? user.getShopAddress() : "Chưa cập nhật")
                .approvalStatus(ApprovalStatus.APPROVED) // Shop được tạo tự động khi duyệt -> đã duyệt luôn
                .build();

        sellerShopRepository.save(shop);

        return user;
    }
}
