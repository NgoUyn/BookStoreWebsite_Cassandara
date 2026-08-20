package com.example.bookstore.service;

import com.example.bookstore.dto.ChangePasswordDTO;
import com.example.bookstore.dto.UserAddressDTO;
import com.example.bookstore.dto.UserProfileDTO;
import com.example.bookstore.model.User;
import com.example.bookstore.model.UserAddress;
import com.example.bookstore.model.UserSecurityEvent;
import com.example.bookstore.repository.UserAddressRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.repository.UserSecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.model.SellerShop;

@Service
@RequiredArgsConstructor
public class BuyerProfileService {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserSecurityEventRepository securityEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final SellerShopRepository sellerShopRepository;

    // ==================== PROFILE MANAGEMENT ====================

    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserProfileDTO dto = UserProfileDTO.fromEntity(user);
        // Attach seller shop info when present
        sellerShopRepository.findBySellerId(userId).ifPresent(shop -> {
            SellerShopResponse resp = SellerShopResponse.builder()
                    .id(shop.getId())
                    .sellerId(shop.getSeller() != null ? shop.getSeller().getId() : null)
                    .slug(shop.getSlug())
                    .shopName(shop.getShopName())
                    .description(shop.getDescription())
                    .logoUrl(shop.getLogoUrl())
                    .bannerUrl(shop.getBannerUrl())
                    .contactEmail(shop.getContactEmail())
                    .contactPhone(shop.getContactPhone())
                    .address(shop.getAddress())
                    .city(shop.getCity())
                    .province(shop.getProvince())
                    .approvalStatus(shop.getApprovalStatus())
                    .createdAt(shop.getCreatedAt())
                    .updatedAt(shop.getUpdatedAt())
                    .build();
            dto.setSellerShop(resp);
        });
        return dto;
    }

    @Transactional
    public UserProfileDTO updateUserProfile(Long userId, UserProfileDTO profileDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        profileDTO.applyToEntity(user);
        User updatedUser = userRepository.save(user);
        
        // Log security event for profile update
        logSecurityEvent(userId, "PROFILE_UPDATED", "User updated their profile information");
        
        return UserProfileDTO.fromEntity(updatedUser);
    }

    // ==================== ADDRESS MANAGEMENT ====================

    public List<UserAddressDTO> getUserAddresses(Long userId) {
        return userAddressRepository.findByUserId(userId).stream()
                .map(UserAddressDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public UserAddressDTO getUserAddressById(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        return UserAddressDTO.fromEntity(address);
    }

    @Transactional
    public UserAddressDTO createUserAddress(Long userId, UserAddressDTO addressDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserAddress address = addressDTO.toEntity();
        address.setUser(user);
        
        // If this is the first address or marked as default, set it as default
        if (userAddressRepository.findByUserId(userId).isEmpty() || Boolean.TRUE.equals(addressDTO.getIsDefault())) {
            address.setIsDefault(true);
            // Unset any previous default addresses
            userAddressRepository.findDefaultAddressByUserId(userId).ifPresent(prev -> {
                prev.setIsDefault(false);
                userAddressRepository.save(prev);
            });
        }
        
        UserAddress savedAddress = userAddressRepository.save(address);
        logSecurityEvent(userId, "ADDRESS_ADDED", "New delivery address added: " + addressDTO.getDistrict());
        
        return UserAddressDTO.fromEntity(savedAddress);
    }

    @Transactional
    public UserAddressDTO updateUserAddress(Long userId, Long addressId, UserAddressDTO addressDTO) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        address.setAddressType(addressDTO.getAddressType());
        address.setRecipientName(addressDTO.getRecipientName());
        address.setRecipientPhone(addressDTO.getRecipientPhone());
        address.setAddressLine(addressDTO.getAddressLine());
        address.setWard(addressDTO.getWard());
        address.setDistrict(addressDTO.getDistrict());
        address.setProvince(addressDTO.getProvince());
        address.setPostalCode(addressDTO.getPostalCode());
        
        if (Boolean.TRUE.equals(addressDTO.getIsDefault()) && !address.getIsDefault()) {
            address.setIsDefault(true);
            // Unset any previous default addresses
            userAddressRepository.findDefaultAddressByUserId(userId).ifPresent(prev -> {
                if (!prev.getId().equals(addressId)) {
                    prev.setIsDefault(false);
                    userAddressRepository.save(prev);
                }
            });
        }
        
        UserAddress updatedAddress = userAddressRepository.save(address);
        logSecurityEvent(userId, "ADDRESS_UPDATED", "Delivery address updated");
        
        return UserAddressDTO.fromEntity(updatedAddress);
    }

    @Transactional
    public void deleteUserAddress(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        userAddressRepository.delete(address);
        logSecurityEvent(userId, "ADDRESS_DELETED", "Delivery address deleted");
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));
        
        // Unset previous default
        userAddressRepository.findDefaultAddressByUserId(userId).ifPresent(prev -> {
            prev.setIsDefault(false);
            userAddressRepository.save(prev);
        });
        
        // Set new default
        address.setIsDefault(true);
        userAddressRepository.save(address);
    }

    // ==================== SECURITY MANAGEMENT ====================

    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify current password
        if (!passwordEncoder.matches(changePasswordDTO.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }
        
        // Check if new password matches confirmation
        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }
        
        // Check password strength
        if (changePasswordDTO.getNewPassword().length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }
        
        // Update password
        user.setPasswordHash(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        userRepository.save(user);
        
        logSecurityEvent(userId, "PASSWORD_CHANGED", "User changed their password");
    }

    public List<UserSecurityEvent> getSecurityEvents(Long userId) {
        return securityEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void logSecurityEvent(Long userId, String eventType, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserSecurityEvent event = UserSecurityEvent.builder()
                .user(user)
                .eventType(eventType)
                .eventDescription(description)
                .build();
        
        securityEventRepository.save(event);
    }
}
