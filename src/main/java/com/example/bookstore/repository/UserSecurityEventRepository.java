package com.example.bookstore.repository;

import com.example.bookstore.model.UserSecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSecurityEventRepository extends JpaRepository<UserSecurityEvent, Long> {
    List<UserSecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    Page<UserSecurityEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    List<UserSecurityEvent> findByUserIdAndEventType(Long userId, String eventType);
}
