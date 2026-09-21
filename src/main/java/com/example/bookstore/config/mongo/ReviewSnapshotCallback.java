package com.example.bookstore.config.mongo;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.BookReview;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * TU DONG LAM DAY SNAPSHOT truoc khi ghi document.
 *
 * <p>Trong mo hinh document, review luu BAN SAO thong tin sach/nguoi dung
 * ({@code review.book{}}, {@code review.user{}}) de hien thi ma khong phai
 * $lookup. Callback nay dam bao moi review duoc ghi deu co snapshot day du,
 * bat ke tang service chi set {@code bookId}/{@code userId}.</p>
 *
 * <p>Tuong tu cach lam cua MongoIdAssignmentCallback: giu tang service sach,
 * khong phai nho set snapshot thu cong.</p>
 */
@Slf4j
@Component
public class ReviewSnapshotCallback implements BeforeConvertCallback<BookReview> {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    /**
     * TIEM LAZY: callback duoc mappingMongoConverter thu thap luc tao bean, nen
     * neu tiem truc tiep repository (-> MongoTemplate -> converter) se tao
     * vong lap dependency.
     */
    public ReviewSnapshotCallback(
            @org.springframework.context.annotation.Lazy BookRepository bookRepository,
            @org.springframework.context.annotation.Lazy UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Override
    public BookReview onBeforeConvert(BookReview review, String collection) {
        try {
            if (review.getBook() == null && review.getBookId() != null) {
                bookRepository.findById(review.getBookId()).ifPresent(review::setBook);
            }
            if (review.getUser() == null && review.getUserId() != null) {
                userRepository.findById(review.getUserId()).ifPresent(review::setUser);
            }
        } catch (Exception e) {
            log.warn("[ReviewSnapshot] khong lam day duoc snapshot cho review {}: {}",
                    review.getId(), e.getMessage());
        }
        return review;
    }
}
