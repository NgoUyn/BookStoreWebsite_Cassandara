package com.example.bookstore.repository;

import com.example.bookstore.model.User;
import com.example.bookstore.model.UserAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FACADE truy vấn {@code UserAddress} tren AGGREGATE {@code users}.
 *
 * <p>Bang {@code user_addresses} da duoc NHUNG vao {@code users.addresses[]}
 * (bounded 1-5 dia chi/nguoi). Lop nay giu API cu de tang service
 * (BuyerProfileService) khong phai viet lai, nhung thao tac that su la
 * doc/sua mang tren document user (1 lan ghi).</p>
 */
@Repository
@RequiredArgsConstructor
public class UserAddressRepository {

    private final UserRepository userRepository;

    public List<UserAddress> findByUserId(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getAddresses() == null ? new ArrayList<UserAddress>() : user.getAddresses())
                .orElseGet(ArrayList::new);
    }

    public Optional<UserAddress> findByIdAndUserId(Long addressId, Long userId) {
        return findByUserId(userId).stream()
                .filter(a -> addressId != null && addressId.equals(a.getId()))
                .findFirst();
    }

    public Optional<UserAddress> findDefaultAddressByUserId(Long userId) {
        return findByUserId(userId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst();
    }

    /**
     * Luu dia di chi khi DA BIET chu so huu (dung khi tao dia chi moi).
     * Dia chi moi duoc them vao mang va (neu la mac dinh) bo mac dinh cua cac
     * dia chi khac - tat ca trong 1 lan ghi.
     */
    public UserAddress saveForUser(Long userId, UserAddress address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getAddresses() == null) {
            user.setAddresses(new ArrayList<>());
        }
        if (address.getId() == null) {
            address.setId(System.currentTimeMillis());   // id tam cho dia chi moi
            address.setCreatedAt(LocalDateTime.now());
        }
        address.setUpdatedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(address.getIsDefault())) {
            user.getAddresses().forEach(a -> a.setIsDefault(false));
        }
        user.getAddresses().removeIf(a -> a.getId() != null && a.getId().equals(address.getId()));
        user.getAddresses().add(address);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return address;
    }

    /** Luu dia chi cu: tim chu so huu theo id cua dia chi. */
    public UserAddress save(UserAddress address) {
        if (address == null || address.getId() == null) {
            throw new IllegalStateException(
                    "Khong xac dinh duoc chu so huu cua dia chi moi - hay dung saveForUser(userId, address)");
        }
        User owner = userRepository.findFirstByAddressesId(address.getId())
                .orElseThrow(() -> new RuntimeException("Address not found"));
        return saveForUser(owner.getId(), address);
    }

    public void delete(UserAddress address) {
        if (address == null || address.getId() == null) {
            return;
        }
        userRepository.findFirstByAddressesId(address.getId()).ifPresent(user -> {
            if (user.getAddresses() != null) {
                user.getAddresses().removeIf(a -> address.getId().equals(a.getId()));
                userRepository.save(user);
            }
        });
    }

    public void deleteByUserId(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setAddresses(new ArrayList<>());
            userRepository.save(user);
        });
    }

    public long countByUserId(Long userId) {
        return findByUserId(userId).size();
    }
}
