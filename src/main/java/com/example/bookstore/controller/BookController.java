package com.example.bookstore.controller;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.bookstore.repository.BookRepository;


import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController // Bao cho SB biet class nay chuyen dung de tao API tra ve du lieu thuong la dinh dang JSON chu khong phai tra ve giao dien HTML
@CrossOrigin("*") // thẻ VIP để có thể ra vào dữ liệu
@RequestMapping("/api/books") // Dat dia chi goc cho toan bo cac API trong class nay
public class BookController {

    @Autowired // Day chinh la co che Dependency Injection quen thuoc tuong tu nhu cach lam viec voi interface trong cac project .NET. Spring Boot se tu dong tiem BookService vao de dung ma khong can thiet phai viet
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SecurityUtils securityUtils;


    @GetMapping // Bao hieu rang ham getAllBooks se duoc chay khi co ai do truy cap vao dia chi goc bang phuong thuc Get nhu khi go link tren trinh duyet
    public Page<Book> getBooks(
            @RequestParam(defaultValue = "0") int page, // Trang số mấy - Mặc định trang 0
            @RequestParam(defaultValue = "20") int size // Lấy bao nhiêu cuốn - Mặc định 20
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return bookRepository.findByApprovalStatus(ApprovalStatus.APPROVED, pageable);
    }

    @GetMapping("/search")
    public Page<Book> searchApprovedBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) List<Long> sellerIds,
            @RequestParam(required = false) List<String> publishers,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String publishYearFrom,
            @RequestParam(required = false) String publishYearTo,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        String authorKeyword = (author == null || author.isBlank()) ? null : author.trim();
        Integer yearFrom = parseYearBound(publishYearFrom);
        Integer yearTo = parseYearBound(publishYearTo);
        List<Long> effectiveCategoryIds = (categoryIds != null && !categoryIds.isEmpty()) ? categoryIds : null;
        List<Long> effectiveSellerIds = (sellerIds != null && !sellerIds.isEmpty()) ? sellerIds : null;
        // Lọc bỏ các publisher không hợp lệ (URL ảnh, chuỗi quá dài)
        List<String> effectivePublishers = null;
        if (publishers != null && !publishers.isEmpty()) {
            effectivePublishers = publishers.stream()
                .filter(p -> p != null && p.length() < 100 && !p.matches("^(https?://|data:image).*") && !p.matches(".*\\.(jpg|jpeg|png|gif|webp|svg|bmp|tiff?)(\\?.*)?$"))
                .collect(java.util.stream.Collectors.toList());
            if (effectivePublishers.isEmpty()) effectivePublishers = null;
        }
        return bookService.searchApprovedBooks(
                keyword,
                effectiveCategoryIds,
                effectiveSellerIds,
                effectivePublishers,
                authorKeyword,
                minPrice,
                maxPrice,
                minRating,
                inStock,
                yearFrom,
                yearTo,
                ApprovalStatus.APPROVED,
                pageable
        );
    }

    private Integer parseYearBound(String rawYear) {
        if (rawYear == null || rawYear.isBlank()) {
            return null;
        }
        return Integer.valueOf(rawYear.trim());
    }

    @GetMapping("/suggestions")
    public List<Book> getSearchSuggestions(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "8") int size) {
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        if (keyword == null) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 12)));
        return bookRepository.findSuggestions(keyword, ApprovalStatus.APPROVED, pageable);
    }

    @GetMapping("/discovery/best-sellers")
    public List<Book> getBestSellingBooks(@RequestParam(defaultValue = "8") int size) {
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 12)));
        return bookRepository.findBestSellingBooks(ApprovalStatus.APPROVED, pageable);
    }

    @GetMapping("/discovery/trending")
    public List<Book> getTrendingBooks(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "8") int size) {
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(size, 12)));
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        return bookRepository.findTrendingBooks(ApprovalStatus.APPROVED, since, pageable);
    }

    /**
     * API lấy danh sách sách của seller bao gồm PENDING, APPROVED, REJECTED
     * Dùng cho trang quản lý kho - S03
     */
    @GetMapping("/seller/me")
    @PreAuthorize("hasAuthority('SELLER')")
    public Page<Book> getSellerBooks(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) return Page.empty();

        String keyword = (q == null || q.trim().isEmpty()) ? null : q.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "isPinned").and(Sort.by(Sort.Direction.DESC, "id")));
        return bookRepository.findBySellerIdAndKeywordAndCategory(sellerId, keyword, categoryId, pageable);
    }

    // API take one book by id (Public API - không security check)
    // Dau ngoac nhon id nghia la gia tri nay se thay doi theo tren Url vd: /api/books/1
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        Book book = bookService.getBookbyId(id);
        // Chỉ trả về sách nếu nó tồn tại VÀ đã được duyệt
        if (book != null && book.getApprovalStatus() == ApprovalStatus.APPROVED) {
            return ResponseEntity.ok(book);
        }
        // Vì lý do bảo mật, trả về 404 cho cả trường hợp không tìm thấy và chưa được duyệt
        return ResponseEntity.notFound().build();
    }

    /**
     * API lấy chi tiết sách của chính seller hiện tại (Bảo mật)
     * Endpoint: GET /api/books/seller/book/{id}
     * Chỉ cho phép seller xem sách của họ
     * Trả về đủ thông tin: book details + seller info
     */
    @GetMapping("/seller/book/{id}")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<?> getSellerOwnBook(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Bạn cần đăng nhập");
        }

        try {
            Book book = bookService.getBookbyId(id);

            if (book == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Sách không tồn tại"));
            }

            // Kiểm tra xem seller có sở hữu sách này không (IDOR Protection)
            if (book.getSeller() == null || !book.getSeller().getId().equals(sellerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(java.util.Map.of("error", "Không có quyền xem sách này"));
            }

            return ResponseEntity.ok(book);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // --- API add new book cho Seller - S03 ---
    // @RequestBody : khi gui 1 cuc dl Json chua thong tin sach SB auto nan JSON do thanh 1 Doi tuong Object Book in Java tinh nang nay same FromBody trong .Net API
    @PostMapping({"", "/seller"})
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<?> createBookForSeller(
            @RequestBody Book book,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Bạn cần đăng nhập để thực hiện hành động này");
        }

        // Chặn đóng mở thẻ - Chống XSS cơ bản
        if (book.getTitle() != null) {
            book.setTitle(book.getTitle().replaceAll("<", "&lt;").replaceAll(">", "&gt;"));
        }

        if (book.getDescription() != null) {
            book.setDescription(securityUtils.sanitize(book.getDescription()));
        }

        try {
            Book createdBook = bookService.addBookForSeller(book, sellerId);
            return ResponseEntity.ok(createdBook);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API Update book cho Seller - S03
    @PutMapping({"/{id}", "/seller/{id}"})
    @PreAuthorize("hasAuthority('SELLER') and hasPermission(#id, 'Book', 'update')")
    public ResponseEntity<?> updateBookForSeller(
            @PathVariable Long id,
            @RequestBody Book bookDetails,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Chặn XSS
        if (bookDetails.getTitle() != null) {
            bookDetails.setTitle(bookDetails.getTitle().replaceAll("<", "&lt;").replaceAll(">", "&gt;"));
        }

        if (bookDetails.getDescription() != null) {
            bookDetails.setDescription(securityUtils.sanitize(bookDetails.getDescription()));
        }

        try {
            Book updatedBook = bookService.updateBookForSeller(id, bookDetails, sellerId);
            return ResponseEntity.ok(updatedBook);
        } catch (RuntimeException e) {
            // Bắt lỗi IDOR từ Service ném lên - IDOR: như việc vượt quyền từ user mà nhảy dc vào seller căng lắm là admin để sửa hoặc thêm sách
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    // API upload ảnh cho sách - S03
    @PostMapping({"/{id}/upload-cover", "/seller/{id}/upload-cover"})
    @PreAuthorize("hasAuthority('SELLER') and hasPermission(#id, 'Book', 'update')")
    public ResponseEntity<?> upLoadBookCover(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            // Check file signature chống RCE - Bảo mật tầng cao
            String imageUrl = bookService.uploadAndVerifyCoverImage(id, file, sellerId);
            return ResponseEntity.ok(imageUrl);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Upload thất bại: " + e.getMessage());
        }
    }

    // Thêm API xóa sách cho Seller (S03)
    @DeleteMapping({"/{id}", "/seller/{id}"})
    @PreAuthorize("hasAuthority('SELLER') and hasPermission(#id, 'Book', 'delete')")
    public ResponseEntity<?> deleteBookForSeller(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            bookService.deleteBookForSeller(id, sellerId);

            // SỬA DÒNG NÀY: Trả về JSON thay vì Text thuần
            return ResponseEntity.ok(java.util.Map.of("message", "Xóa sách thành công!"));

        } catch (RuntimeException e) {

            // SỬA DÒNG NÀY: Bọc cái lỗi vào JSON luôn
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message", e.getMessage()));

        }
    }


    @PatchMapping({"/seller/{id}/pin"})
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<?> togglePinBook(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedPrincipal principal
    ) {
        Long sellerId = currentSellerId(principal);
        if (sellerId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            Book book = bookService.getBookbyId(id);
            if (book == null || book.getSeller() == null || !book.getSeller().getId().equals(sellerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message", "Không có quyền"));
            }
            book.setPinned(!book.isPinned());
            bookRepository.save(book);
            return ResponseEntity.ok(java.util.Map.of("message", "Đã cập nhật ghim", "isPinned", book.isPinned()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    private Long currentSellerId(JwtAuthenticatedPrincipal principal) {
        if (principal != null) {
            return principal.sellerId() != null ? principal.sellerId() : principal.userId();
        }
        return null;
    }

    private Sort resolveSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "id");
        }

        return switch (sort.toLowerCase()) {
            case "price-asc" -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "title-asc" -> Sort.by(Sort.Direction.ASC, "title");
            case "author-asc" -> Sort.by(Sort.Direction.ASC, "author");
            case "latest" -> Sort.by(Sort.Direction.DESC, "id");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }
}