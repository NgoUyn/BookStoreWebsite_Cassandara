package com.example.bookstore.repository;

import com.example.bookstore.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Repository collection notifications. */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    long countByUserId(Long userId);

    /**
     * Danh dau tat ca thong bao da doc bang 1 update (thay JPQL {@code @Modifying}
     * cua ban cu) - MongoDB updateMulti chi 1 round-trip.
     */
    @Query("{ 'userId': ?0, 'isRead': false }")
    @Update("{ $set: { 'isRead': true, 'readAt': ?1 } }")
    long markAllAsReadByUserId(Long userId, LocalDateTime readAt);

    void deleteByUserId(Long userId);
}
