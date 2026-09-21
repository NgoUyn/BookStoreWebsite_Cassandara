package com.example.bookstore.repository;

import com.example.bookstore.model.SupportTicket;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** Repository collection support_tickets. */
@Repository
public interface SupportTicketRepository extends MongoRepository<SupportTicket, Long> {

    long countByUserId(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    long countByStatus(String status);
}
