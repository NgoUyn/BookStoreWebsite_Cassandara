package com.example.bookstore.repository;

import com.example.bookstore.model.PaymentTransaction;
import com.example.bookstore.model.enums.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repository collection payment_transactions. */
@Repository
public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByOrderId(Long orderId);

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    List<PaymentTransaction> findByStatus(PaymentStatus status);

    List<PaymentTransaction> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    long countByStatus(PaymentStatus status);
}
