package com.example.bookstore.service;

import com.example.bookstore.dto.CartItemResponse;
import com.example.bookstore.dto.CartItemUpsertRequest;
import com.example.bookstore.dto.CartResponse;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.Cart;
import com.example.bookstore.model.CartItem;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CartItemRepository;
import com.example.bookstore.repository.CartRepository;
import com.example.bookstore.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public CartResponse getBuyerCart(Long buyerId) {
        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().buyer(buyer).build()));
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(Long buyerId, CartItemUpsertRequest request) {
        User buyer = requireBuyer(buyerId);
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        // Buyer only sees and buys approved books.
        if (book.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book is not available for sale");
        }

        if (book.getStockQuantity() == null || book.getStockQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book is out of stock");
        }

        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().buyer(buyer).build()));

        CartItem item = cartItemRepository.findByCartIdAndBookId(cart.getId(), book.getId())
                .orElseGet(() -> {
                    CartItem newItem = CartItem.builder().cart(cart).book(book).quantity(0).build();

                    // ✅ VÁ LỖI CHÍ MẠNG TẠI ĐÂY: Khởi tạo mảng rỗng nếu items đang bị Null
                    if (cart.getItems() == null) {
                        cart.setItems(new ArrayList<>());
                    }

                    // ĐỒNG BỘ CACHE: Thêm item mới vào thẳng list của Cart
                    cart.getItems().add(newItem);
                    return newItem;
                });

        int newQuantity = item.getQuantity() + request.getQuantity();
        if (newQuantity > book.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity exceeds stock");
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long buyerId, Long itemId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }

        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));

        Book book = item.getBook();
        if (book.getStockQuantity() != null && quantity > book.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity exceeds stock");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long buyerId, Long itemId) {
        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));

        cart.getItems().remove(item);

        cartItemRepository.delete(item);

        return toCartResponse(cart);
    }
    private User requireBuyer(Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));
        if (buyer.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a buyer");
        }
        return buyer;
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> rows = new ArrayList<>();
        int totalItems = 0;
        double totalAmount = 0.0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Book book = item.getBook();
                double unitPrice = book.getPrice() == null ? 0.0 : book.getPrice();
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                double lineTotal = unitPrice * qty;
                totalItems += qty;
                totalAmount += lineTotal;

                String sellerName = book.getSeller() == null
                        ? null
                        : (book.getSeller().getShopName() == null
                        ? book.getSeller().getUsername()
                        : book.getSeller().getShopName());

                rows.add(CartItemResponse.builder()
                        .itemId(item.getId())
                        .bookId(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .unitPrice(unitPrice)
                        .quantity(qty)
                        .lineTotal(lineTotal)
                        .sellerId(book.getSeller() == null ? null : book.getSeller().getId())
                        .sellerName(sellerName)
                        .imageUrl(book.getImageUrl())
                        .build());
            }
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .buyerId(cart.getBuyer() == null ? null : cart.getBuyer().getId())
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .items(rows)
                .build();
    }
}
