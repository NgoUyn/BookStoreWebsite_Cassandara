package com.example.bookstore.service;

import com.example.bookstore.dto.WishlistActionResponse;
import com.example.bookstore.dto.WishlistItemResponse;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.UserRole;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * NGHIEP VU WISHLIST - phien ban MongoDB.
 *
 * <p>Ban SQL dung bang trung gian {@code user_wishlist_books} (many-to-many)
 * => moi lan xem wishlist phai JOIN. Nay chi luu mang id trong document user
 * ({@code wishlistBookIds[]}) + tang/giam counter {@code books.stats.wishlistCount}
 * de xep hang "duoc yeu thich nhat" ma khong can join nguoc.</p>
 */
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(Long userId) {
        User user = findBuyer(userId);
        List<Long> ids = user.getWishlistBookIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Book> books = bookRepository.findByIdIn(ids);
        books.sort(Comparator.comparing(Book::getId,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<WishlistItemResponse> out = new ArrayList<>();
        for (Book book : books) {
            out.add(toWishlistItem(book));
        }
        return out;
    }

    @Transactional
    public WishlistActionResponse toggleWishlist(Long userId, Long bookId) {
        User user = findBuyer(userId);
        Book book = findBook(bookId);

        if (user.getWishlistBookIds() == null) {
            user.setWishlistBookIds(new ArrayList<>());
        }

        boolean removed = user.getWishlistBookIds().removeIf(id -> Objects.equals(id, bookId));
        if (!removed) {
            user.getWishlistBookIds().add(bookId);
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

        if (user.getWishlistBookIds() != null) {
            user.getWishlistBookIds().removeIf(id -> Objects.equals(id, bookId));
            userRepository.save(user);
        }

        List<WishlistItemResponse> items = getWishlist(userId);
        return WishlistActionResponse.builder()
                .saved(false)
                .count(items.size())
                .items(items)
                .build();
    }

    /** Cau hoi nguoc: bao nhieu nguoi dang quan tam 1 cuon sach (multikey index). */
    @Transactional(readOnly = true)
    public long countWishlistByBook(Long bookId) {
        return userRepository.countWishlistByBookId(bookId);
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
                .categoryName(book.getCategoryName())
                .shopName(book.getSeller() != null ? book.getSeller().getShopName() : null)
                .build();
    }

    /** Thoi diem cap nhat wishlist gan nhat (ho tro hien thi "moi cap nhat"). */
    @Transactional(readOnly = true)
    public LocalDateTime lastUpdated(Long userId) {
        return userRepository.findById(userId).map(User::getUpdatedAt).orElse(null);
    }
}
