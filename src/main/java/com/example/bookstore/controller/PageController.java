package com.example.bookstore.controller;

import com.example.bookstore.config.JwtUtil;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import com.example.bookstore.security.JwtAuthenticatedPrincipal;
import com.example.bookstore.service.recommendation.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class PageController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private SellerShopRepository sellerShopRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    // Khi người dùng gõ link: localhost:8080/book/1
    @GetMapping("/book/{id}")
    public String viewBookDetail(@PathVariable Long id, Model model, Authentication authentication) {

        // 1. Chui vào kho tìm sách theo ID
        Book book = bookRepository.findById(id).orElse(null);

        // 2. Không thấy sách thì đá về trang chủ
        if (book == null) {
            return "redirect:/";
        }

        // 3. Inject authentication data cho JS (chat, v.v.)
        Long authUserId = null;
        String authUserRole = null;
        String authAccessToken = null;

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
                authUserId = jwtPrincipal.userId();
                authUserRole = jwtPrincipal.roles() != null && !jwtPrincipal.roles().isEmpty()
                    ? jwtPrincipal.roles().get(0) : null;
            } else if (principal instanceof User user) {
                authUserId = user.getId();
                authUserRole = user.getRole() != null ? user.getRole().name() : null;
            }

            // Generate JWT token for the authenticated user (for JS to use)
            if (authUserId != null && authUserRole != null) {
                try {
                    User user = userRepository.findById(authUserId).orElse(null);
                    if (user != null) {
                        authAccessToken = jwtUtil.generateToken(user);
                    }
                } catch (Exception e) {
                    // Token generation failed silently - frontend will use X-User-Id fallback
                }
            }
        }

        model.addAttribute("authUserId", authUserId);
        model.addAttribute("authUserRole", authUserRole != null ? authUserRole : "GUEST");
        model.addAttribute("authAccessToken", authAccessToken);

        // 4. Load 2 luồng gợi ý
        List<Book> boughtTogether = recommendationService.getBoughtTogetherBooks(id);
        List<Book> similarBooks = recommendationService.getSimilarBooks(id);

        // 5. Lấy sách bỏ vào Model để shipper mang sang file HTML
        model.addAttribute("book", book);
        model.addAttribute("boughtTogetherBooks", boughtTogether);
        model.addAttribute("similarBooks", similarBooks);

        // 6. Mở file BookDetail.html
        return "main/Details_Produce";
    }

        @GetMapping("/shop/{sellerId}")
        public String viewPublicShopBySellerId(@PathVariable Long sellerId, Model model) {
            var shopOpt = sellerShopRepository.findBySellerId(sellerId);
            if (shopOpt.isEmpty()) {
                return "redirect:/";
            }

            SellerShop shop = shopOpt.get();

            model.addAttribute("shop", shop);
            model.addAttribute("shopSlug", shop.getSlug());
            return "main/Shop_Public";
        }
}
