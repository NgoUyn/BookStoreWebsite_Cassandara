package com.example.bookstore.service;

import com.example.bookstore.dto.WishlistActionResponse;
import com.example.bookstore.dto.WishlistItemResponse;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class WishlistService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(Long userId) {
        User user = findBuyer(userId);
        return user.getWishlistBooks().stream()
            .sorted(Comparator.comparing(Book::getId, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .map(this::toWishlistItem)
            .toList();
    }

    @Transactional
    public WishlistActionResponse toggleWishlist(Long userId, Long bookId) {
        User user = findBuyer(userId);
        Book book = findBook(bookId);

        boolean removed = user.getWishlistBooks().removeIf(item -> Objects.equals(item.getId(), book.getId()));
        if (!removed) {
            user.getWishlistBooks().add(book);
        }

        userRepository.save(user);
        List<WishlistItemResponse> items = getWishlist(userId);
        return WishlistActionResponse.builder()
            .saved(!removed)
            .count(items.size())
            .items(items)
            .build();
    }

    @Transactional
    public WishlistActionResponse removeFromWishlist(Long userId, Long bookId) {
        User user = findBuyer(userId);
        findBook(bookId);

        user.getWishlistBooks().removeIf(item -> Objects.equals(item.getId(), bookId));
        userRepository.save(user);

        List<WishlistItemResponse> items = getWishlist(userId);
        return WishlistActionResponse.builder()
            .saved(false)
            .count(items.size())
            .items(items)
            .build();
    }

    private User findBuyer(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui long dang nhap");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chi buyer moi co Wishlist");
        }

        return user;
    }

    private Book findBook(Long bookId) {
        if (bookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book id khong hop le");
        }

        return bookRepository.findById(bookId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    private WishlistItemResponse toWishlistItem(Book book) {
        return WishlistItemResponse.builder()
            .id(book.getId())
            .title(book.getTitle())
            .author(book.getAuthor())
            .price(book.getPrice())
            .stockQuantity(book.getStockQuantity())
            .imageUrl(book.getImageUrl())
            .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
            .shopName(book.getSeller() != null ? book.getSeller().getShopName() : null)
            .build();
    }
}
