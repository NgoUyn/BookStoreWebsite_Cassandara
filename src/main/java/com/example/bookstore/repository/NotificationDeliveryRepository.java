package com.example.bookstore.repository;

import com.example.bookstore.model.NotificationDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository collection notification_deliveries.
 *
 * <p>Queue worker poll moi giay bang query {@code {status, nextRetryAt}}
 * - dung index {@code idx_nd_pending_retry} (xem db/mongo/02_indexes.js).</p>
 */
@Repository
public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, Long> {

    @Query("{ 'status': 'PENDING', 'nextRetryAt': { $lte: ?0 } }")
    List<NotificationDelivery> findPendingRetries(LocalDateTime now, Pageable pageable);

    List<NotificationDelivery> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);

    Page<NotificationDelivery> findByStatusOrderByCreatedAtDesc(NotificationDelivery.DeliveryStatus status,
                                                                Pageable pageable);

    long countByStatus(NotificationDelivery.DeliveryStatus status);

    List<NotificationDelivery> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByChannelAndStatus(String channel, NotificationDelivery.DeliveryStatus status);

    /** Cac ban ghi dang cho gui (de dashboard/health check dem nhanh). */
    @Query(value = "{ 'status': 'PENDING' }", count = true)
    long countPending();

    void deleteByNotificationId(Long notificationId);
}
