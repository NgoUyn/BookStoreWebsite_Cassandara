package com.example.bookstore.repository;

import com.example.bookstore.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Repository collection customer_ml (ho so ML, quan he 1-1 voi users qua userId). */
@Repository
public interface CustomerRepository extends MongoRepository<Customer, Long> {

    Optional<Customer> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
