package com.example.bookstore.repository;

import com.example.bookstore.model.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserId(Long userId);
    
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);
    
    @Query("SELECT ua FROM UserAddress ua WHERE ua.user.id = ?1 AND ua.isDefault = true")
    Optional<UserAddress> findDefaultAddressByUserId(Long userId);
    
    @Query("DELETE FROM UserAddress ua WHERE ua.user.id = ?1")
    void deleteByUserId(Long userId);
}
