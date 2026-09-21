package com.example.bookstore.service.recommendation;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationFallbackEngine {

    @Autowired
    private BookRepository bookRepository;

    public List<Book> fallbackSameAuthorOrCategory(Book sourceBook, int limit, List<Book> excludeList) {
        if (sourceBook == null || limit <= 0) return Collections.emptyList();

        Set<Long> excludedIds = excludeList == null ? new HashSet<>()
                : excludeList.stream().map(Book::getId).collect(Collectors.toSet());
        excludedIds.add(sourceBook.getId());

        List<Book> result = new ArrayList<>();
        final Long sourceCategoryId = sourceBook.getCategoryId();

        // ------------------------------------------------------------------
        // LUU Y HIEU NANG: ban cu lam `bookRepository.findByApprovalStatus(APPROVED)`
        // -> nap TOAN BO sach vao RAM roi sort. Voi 248k sach (import tu Books.csv)
        // moi lan goi mat ~35 giay. Nay day viec loc xuong DB + gioi han ket qua
        // (dung index: author+approvalStatus+isActive, va idx_books_catalog).
        // ------------------------------------------------------------------

        // 1) Uu tien sach cung tac gia (index idx_books_author_status)
        String author = sourceBook.getAuthor();
        if (author != null && !author.isBlank()) {
            Pageable page = PageRequest.of(0, limit + excludedIds.size() + 5);
            for (Book b : bookRepository.findByAuthorAndApprovalStatusAndIsActiveTrueOrderByIdDesc(
                    author, ApprovalStatus.APPROVED, page)) {
                if (result.size() >= limit) break;
                if (!excludedIds.contains(b.getId())) result.add(b);
            }
        }

        // 2) Neu chua du -> lay sach cung danh muc (index idx_books_catalog)
        if (result.size() < limit && sourceCategoryId != null) {
            Pageable page = PageRequest.of(0, limit + excludedIds.size() + 5);
            for (Book b : bookRepository.findByCategoryIdAndIdNotAndApprovalStatusAndIsActiveTrue(
                    sourceCategoryId, sourceBook.getId(), ApprovalStatus.APPROVED, page)) {
                if (result.size() >= limit) break;
                if (excludedIds.contains(b.getId())) continue;
                boolean duplicated = result.stream().anyMatch(r -> r.getId().equals(b.getId()));
                if (!duplicated) result.add(b);
            }
        }

        return result;
    }
}
