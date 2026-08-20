package com.example.bookstore.controller;

import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.Coupon;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.service.CouponService;
import com.example.bookstore.service.SellerShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class MainPageController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SellerShopService sellerShopService;

    @Autowired
    private SellerShopRepository sellerShopRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CouponService couponService;

    @GetMapping("/")
    public String home(Model model) {
        // Cập nhật: Lấy 20 cuốn đã duyệt, đang bán (active) và sắp xếp theo ID mới nhất
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id").descending());
        model.addAttribute("books", bookRepository.findByApprovalStatusAndIsActiveTrue(ApprovalStatus.APPROVED, pageable).getContent());
        return "main/index";
    }

    @GetMapping("/main/index.html")
    public String homeHtmlAlias() {
        return "main/index";
    }

    @GetMapping("/main/discovery")
    public String discovery(Model model) {
        // Tương tự trang chủ, Trang khám phá cũng chỉ nên hiển thị sách hợp lệ
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id").descending());
        model.addAttribute("books", bookRepository.findByApprovalStatusAndIsActiveTrue(ApprovalStatus.APPROVED, pageable).getContent());
        return "main/Discovery_Page";
    }

    @GetMapping("/main/Discovery_Page.html")
    public String discoveryHtmlAlias() {
        return "main/Discovery_Page";
    }

    @GetMapping("/main/auth")
    public String auth() {
        return "main/Auth_Page";
    }

    @GetMapping("/main/Auth_Page.html")
    public String authHtmlAlias() {
        return "main/Auth_Page";
    }

    @GetMapping("/main/cart")
    public String cart() {
        return "main/Cart_Page";
    }

    @GetMapping("/main/Cart_Page.html")
    public String cartHtmlAlias() {
        return "main/Cart_Page";
    }

    @GetMapping("/main/contact")
    public String contact() {
        return "main/Contact_us";
    }

    @GetMapping("/main/Contact_us.html")
    public String contactHtmlAlias() {
        return "main/Contact_us";
    }

    @GetMapping("/main/checkout")
    public String checkout() {
        return "main/Checkout_Page";
    }

    @GetMapping("/main/Checkout_Page.html")
    public String checkoutHtmlAlias() {
        return "main/Checkout_Page";
    }

    @GetMapping("/main/order-details")
    public String orderDetails() {
        return "main/Order_Details";
    }

    @GetMapping("/main/Order_Details.html")
    public String orderDetailsHtmlAlias() {
        return "main/Order_Details";
    }

    @GetMapping("/main/order-success")
    public String orderSuccess() {
        return "main/Order_Success";
    }

    @GetMapping("/main/Order_Success.html")
    public String orderSuccessHtmlAlias() {
        return "main/Order_Success";
    }

    @GetMapping("/main/search")
    public String searchResult() {
        return "main/Search_Result";
    }

    @GetMapping("/main/Search_Result.html")
    public String searchResultHtmlAlias() {
        return "main/Search_Result";
    }

    @GetMapping("/main/flash-sale")
    public String flashSale() {
        return "main/Flash_Sale";
    }

    @GetMapping("/main/Flash_Sale.html")
    public String flashSaleHtmlAlias() {
        return "main/Flash_Sale";
    }

    @GetMapping("/main/payment-result")
    public String paymentResult() {
        return "main/Payment_Result";
    }

    @GetMapping("/main/Payment_Result.html")
    public String paymentResultHtmlAlias() {
        return "main/Payment_Result";
    }

    @GetMapping("/buyer/dashboard")
    public String buyerDashboard() {
        return "buyer/Buyer_Profile_Dashboard";
    }
}