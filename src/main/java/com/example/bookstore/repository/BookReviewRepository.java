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
}
