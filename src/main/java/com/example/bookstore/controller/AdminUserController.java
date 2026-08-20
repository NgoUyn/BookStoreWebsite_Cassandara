package com.example.bookstore.controller;

import com.example.bookstore.model.User;
import com.example.bookstore.service.UserService;
import com.example.bookstore.service.SellerShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/admin/users")
public class AdminUserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SellerShopService sellerShopService;
    
    /**
     * Khóa tài khoản người dùng
     * Endpoint: PUT /api/admin/users/{id}/lock
     */
    @PutMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> lockUser(
            @PathVariable Long id
    ) {
        try {
            User user = userService.lockUser(id);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }
    
    /**
     * Mở khóa tài khoản người dùng
     * Endpoint: PUT /api/admin/users/{id}/unlock
     */
    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> unlockUser(
            @PathVariable Long id
    ) {
        try {
            User user = userService.unlockUser(id);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }
    
    /**
     * Lấy thông tin người dùng theo ID
     * Endpoint: GET /api/admin/users/{id}
     */
    @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getUserById(
            @PathVariable Long id
    ) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User không tồn tại");
        }
        return ResponseEntity.ok(user);
    }

    /**
     * Admin duyệt người bán (tạo/cập nhật SellerShop với trạng thái APPROVED)
     * Endpoint: PUT /api/admin/users/{id}/approve-seller
     * 
     * This endpoint:
     * 1. Validates user has SELLER role
     * 2. Auto-creates SellerShop if not exists
     * 3. Sets SellerShop approval status to APPROVED
     * 4. Seller can now access order management features
     */
    @PutMapping("/{id}/approve-seller")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approveSeller(
            @PathVariable Long id
    ) {
        try {
            var shopResponse = sellerShopService.adminApproveSeller(id);
            return ResponseEntity.ok(shopResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}
