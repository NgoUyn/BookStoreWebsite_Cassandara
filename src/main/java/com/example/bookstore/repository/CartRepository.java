package com.example.bookstore.repository;

import com.example.bookstore.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository AGGREGATE carts - CHI CON 1 repository (ban SQL co 2:
 * CartRepository + CartItemRepository). Viec them/xoa/sua item duoc thuc hien
 * bang $push/$pull/$set tren chinh document carts.
 */
@Repository
public interface CartRepository extends MongoRepository<Cart, Long> {

    Optional<Cart> findByBuyerId(Long buyerId);

    void deleteByBuyerId(Long buyerId);

    boolean existsByBuyerId(Long buyerId);
}
