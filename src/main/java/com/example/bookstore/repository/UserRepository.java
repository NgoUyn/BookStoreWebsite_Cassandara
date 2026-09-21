package com.example.bookstore.repository;

import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository AGGREGATE users (MongoDB).
 *
 * <p>Chuyen tu JpaRepository sang MongoRepository: cac method derived query
 * gan nhu giu nguyen ten, chi khac dieu kien tham so la ID thay vi entity.</p>
 */
@Repository
public interface UserRepository extends MongoRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findAllByRole(UserRole role);

    Page<User> findByRole(UserRole role, Pageable pageable);

    List<User> findByIsActive(boolean isActive);

    List<User> findByRoleAndIsActive(UserRole role, boolean isActive);

    List<User> findByUsernameContainingIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    /**
     * Truy van nguoc: ai da them sach vao wishlist (multikey index
     * {@code idx_users_wishlist_books}) - ban SQL phai JOIN bang trung gian.
     */
    List<User> findByWishlistBookIdsContaining(Long bookId);

    /** Dem nhanh so nguoi quan tam 1 cuon sach (dung counter wishlistCount). */
    @Query(value = "{ 'wishlistBookIds': ?0 }", count = true)
    long countWishlistByBookId(Long bookId);
}
