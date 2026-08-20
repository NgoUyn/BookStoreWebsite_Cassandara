package com.example.bookstore.repository;

import com.example.bookstore.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
	Optional<CartItem> findByIdAndCartId(Long itemId, Long cartId);

	Optional<CartItem> findByCartIdAndBookId(Long cartId, Long bookId);
}
