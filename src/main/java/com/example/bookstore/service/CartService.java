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
import com.example.bookstore.repository.CartRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.service.mongo.MongoSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NGHIEP VU GIO HANG - phien ban MongoDB.
 *
 * <p>So sanh ban SQL Server: truoc day phai thao tac 2 bang
 * ({@code carts} + {@code cart_items}) qua 2 repository + JOIN moi lan doc;
 * nay gio hang la MOT document {@code carts} chua {@code items[]} => moi thao
 * tac them/xoa/sua chi la 1 lan save (atomic tren 1 document), doc gio hang
 * khong con JOIN.</p>
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CartRepository cartRepository;
    private final MongoSequenceService sequenceService;

    @Transactional
    public CartResponse getBuyerCart(Long buyerId) {
        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseGet(() -> createCart(buyer));
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(Long buyerId, CartItemUpsertRequest request) {
        User buyer = requireBuyer(buyerId);
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        if (book.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book is not available for sale");
        }
        if (book.getStockQuantity() == null || book.getStockQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book is out of stock");
        }

        Cart cart = cartRepository.findByBuyerId(buyer.getId()).orElseGet(() -> createCart(buyer));

        CartItem item = cart.findItemByBookId(book.getId());
        int currentQty = item == null ? 0 : (item.getQuantity() == null ? 0 : item.getQuantity());
        int newQuantity = currentQty + request.getQuantity();
        if (newQuantity > book.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity exceeds stock");
        }

        if (item == null) {
            item = CartItem.builder()
                    .id(sequenceService.nextId("cart_items"))
                    .quantity(newQuantity)
                    .addedAt(LocalDateTime.now())
                    .build();
            item.applyBookSnapshot(book);
            cart.getItems().add(item);
        } else {
            item.setQuantity(newQuantity);
            item.applyBookSnapshot(book);   // cap nhat gia/ten moi nhat
        }

        cart.recalculateTotals();
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long buyerId, Long itemId, Integer quantity) {
        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));

        CartItem item = cart.findItemById(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        }

        // Ton kho luon duoc kiem tra "live" tu collection books (khong dung snapshot)
        Book book = bookRepository.findById(item.getBookId()).orElse(null);
        if (book != null && book.getStockQuantity() != null && quantity > book.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity exceeds stock");
        }

        item.setQuantity(quantity);
        cart.recalculateTotals();
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long buyerId, Long itemId) {
        User buyer = requireBuyer(buyerId);
        Cart cart = cartRepository.findByBuyerId(buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));

        CartItem item = cart.findItemById(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        cart.getItems().remove(item);
        cart.recalculateTotals();
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    private Cart createCart(User buyer) {
        Cart cart = Cart.builder()
                .buyerId(buyer.getId())
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .schemaVersion(1)
                .build();
        cart.recalculateTotals();
        return cartRepository.save(cart);
    }

    private User requireBuyer(Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer not found"));
        if (buyer.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a buyer");
        }
        return buyer;
    }

    /**
     * Chuyen document cart thanh DTO - KHONG can doc collection books vi item
     * da luu snapshot (title/unitPrice/imageUrl/sellerName).
     */
    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> rows = new ArrayList<>();
        int totalItems = 0;
        double totalAmount = 0.0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                double unitPrice = item.getUnitPrice() == null ? 0.0 : item.getUnitPrice();
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                double lineTotal = unitPrice * qty;
                totalItems += qty;
                totalAmount += lineTotal;

                rows.add(CartItemResponse.builder()
                        .itemId(item.getId())
                        .bookId(item.getBookId())
                        .title(item.getTitle())
                        .author(item.getAuthor())
                        .unitPrice(unitPrice)
                        .quantity(qty)
                        .lineTotal(lineTotal)
                        .sellerId(item.getSellerId())
                        .sellerName(item.getSellerName())
                        .imageUrl(item.getImageUrl())
                        .build());
            }
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .buyerId(cart.getBuyerId())
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .items(rows)
                .build();
    }
}
