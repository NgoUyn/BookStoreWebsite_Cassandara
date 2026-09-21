package com.example.bookstore.repository;

import com.example.bookstore.model.BookReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository collection reviews (aggregate root #6).
 *
 * <p>Cac truy vấn cua ban SQL ({@code AVG}, {@code GROUP BY rating}) nay duoc
 * thay bang: (a) doc read-model {@code books.rating} khi hien thi, hoac
 * (b) aggregation {@code $group} khi can tinh lai (job $merge).</p>
 */
@Repository
public interface BookReviewRepository extends MongoRepository<BookReview, Long> {

    Page<BookReview> findByBookIdAndIsHiddenFalse(Long bookId, Pageable pageable);

    Page<BookReview> findByBookId(Long bookId, Pageable pageable);

    Page<BookReview> findByBookIdAndRatingAndIsHiddenFalse(Long bookId, Integer rating, Pageable pageable);

    long countByBookIdAndIsHiddenFalse(Long bookId);

    long countByBookId(Long bookId);

    boolean existsByBookIdAndUserId(Long bookId, Long userId);

    Optional<BookReview> findByBookIdAndUserId(Long bookId, Long userId);

    Page<BookReview> findByUserIdAndIsHiddenFalse(Long userId, Pageable pageable);

    long countByUserIdAndIsHiddenFalse(Long userId);

    long countByUserId(Long userId);

    List<BookReview> findByBookIdAndIsHiddenFalseOrderByHelpfulCountDesc(Long bookId, Pageable pageable);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    // ======================================================================
    // AGGREGATION (thay AVG()/GROUP BY cua SQL Server)
    // ======================================================================

    /** Diem trung binh cua 1 sach (chi tinh review dang hien). */
    @Aggregation(pipeline = {
            "{ $match: { 'bookId': ?0, 'isHidden': false } }",
            "{ $group: { '_id': null, avg: { $avg: '$rating' } } }"
    })
    Double findAverageRatingByBookId(Long bookId);

    /** Diem trung binh ma 1 nguoi dung da cho (feature ML). */
    @Aggregation(pipeline = {
            "{ $match: { 'userId': ?0, 'isHidden': false } }",
            "{ $group: { '_id': null, avg: { $avg: '$rating' } } }"
    })
    Double findAverageRatingByUserId(Long userId);

    /** Thong ke so luong theo tung muc sao (thay GROUP BY rating). */
    @Aggregation(pipeline = {
            "{ $match: { 'bookId': ?0, 'isHidden': false } }",
            "{ $group: { '_id': '$rating', count: { $sum: 1 } } }",
            "{ $sort: { '_id': 1 } }"
    })
    List<java.util.Map> countRatingDistributionByBookId(Long bookId);

    // ======================================================================
    // LOP TUONG THICH (adapter): code cu truyen entity Book/User
    // ======================================================================

    default Page<BookReview> findByBookAndIsHiddenFalse(com.example.bookstore.model.Book book, Pageable pageable) {
        return findByBookIdAndIsHiddenFalse(book.getId(), pageable);
    }

    default Page<BookReview> findByBookAndRatingAndIsHiddenFalse(com.example.bookstore.model.Book book,
                                                                Integer rating, Pageable pageable) {
        return findByBookIdAndRatingAndIsHiddenFalse(book.getId(), rating, pageable);
    }

    default Page<BookReview> findAllByBook(com.example.bookstore.model.Book book, Pageable pageable) {
        return findByBookId(book.getId(), pageable);
    }

    default long countByBookAndIsHiddenFalse(com.example.bookstore.model.Book book) {
        return countByBookIdAndIsHiddenFalse(book.getId());
    }

    default Double findAverageRatingByBook(com.example.bookstore.model.Book book) {
        return findAverageRatingByBookId(book.getId());
    }

    default List<java.util.Map> countRatingDistributionByBook(com.example.bookstore.model.Book book) {
        return countRatingDistributionByBookId(book.getId());
    }

    default boolean existsByBookAndUser(com.example.bookstore.model.Book book,
                                        com.example.bookstore.model.User user) {
        return existsByBookIdAndUserId(book.getId(), user.getId());
    }

    default Optional<BookReview> findByBookAndUser(com.example.bookstore.model.Book book,
                                                   com.example.bookstore.model.User user) {
        return findByBookIdAndUserId(book.getId(), user.getId());
    }

    default Page<BookReview> findByUserAndIsHiddenFalse(com.example.bookstore.model.User user, Pageable pageable) {
        return findByUserIdAndIsHiddenFalse(user.getId(), pageable);
    }

    default Double findAverageRatingByUser(com.example.bookstore.model.User user) {
        return findAverageRatingByUserId(user.getId());
    }

    default long countByUser(com.example.bookstore.model.User user) {
        return countByUserId(user.getId());
    }
}
