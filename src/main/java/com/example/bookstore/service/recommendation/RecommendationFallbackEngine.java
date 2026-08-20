package com.example.bookstore.service.recommendation;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationFallbackEngine {

    @Autowired
    private BookRepository bookRepository;

    public List<Book> fallbackSameAuthorOrCategory(Book sourceBook, int limit, List<Book> excludeList) {
        if (sourceBook == null || limit <= 0) return Collections.emptyList();

        List<Book> candidates = bookRepository.findByApprovalStatus(ApprovalStatus.APPROVED);

        Set<Long> excludedIds = excludeList == null ? new HashSet<>() : excludeList.stream().map(Book::getId).collect(Collectors.toSet());
        excludedIds.add(sourceBook.getId());

        final String sourceAuthor = sourceBook.getAuthor();
        final Long sourceCategoryId = sourceBook.getCategory() == null ? null : sourceBook.getCategory().getId();

        return candidates.stream()
            .filter(b -> !excludedIds.contains(b.getId()))
            .sorted((b1, b2) -> {
                boolean sameAuthor1 = Objects.equals(b1.getAuthor(), sourceAuthor);
                boolean sameAuthor2 = Objects.equals(b2.getAuthor(), sourceAuthor);
                if (sameAuthor1 && !sameAuthor2) return -1;
                if (!sameAuthor1 && sameAuthor2) return 1;

                Long c1 = b1.getCategory() == null ? null : b1.getCategory().getId();
                Long c2 = b2.getCategory() == null ? null : b2.getCategory().getId();

                boolean sameCat1 = Objects.equals(c1, sourceCategoryId);
                boolean sameCat2 = Objects.equals(c2, sourceCategoryId);
                if (sameCat1 && !sameCat2) return -1;
                if (!sameCat1 && sameCat2) return 1;
                return 0;
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
}
