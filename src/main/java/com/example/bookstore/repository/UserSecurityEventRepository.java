package com.example.bookstore.repository;

import com.example.bookstore.model.UserSecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Repository collection user_security_events (nhat ky bao mat). */
@Repository
public interface UserSecurityEventRepository extends MongoRepository<UserSecurityEvent, Long> {

    List<UserSecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserSecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserSecurityEvent> findByUserIdAndEventTypeOrderByCreatedAtDesc(Long userId, String eventType);

    long countByUserId(Long userId);
}
