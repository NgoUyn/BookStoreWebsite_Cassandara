package com.example.bookstore.repository;

import com.example.bookstore.model.PaymentTransaction;
import com.example.bookstore.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByOrderId(Long orderId);

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    List<PaymentTransaction> findByStatus(PaymentStatus status);

    List<PaymentTransaction> findByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
