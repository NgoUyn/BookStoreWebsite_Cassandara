// PanelPageController : dùng để điều hướng các trang admin và seller, mỗi phương thức sẽ trả về một view tương ứng với trang đó, đồng thời truyền vào model các thuộc tính như pageTitle, pageSubtitle và activeMenu để hiển thị thông tin trên giao diện và đánh dấu menu đang hoạt động.
package com.example.bookstore.controller;

import com.example.bookstore.config.JwtUtil;
import com.example.bookstore.model.Book;
import com.example.bookstore.model.Category;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.repository.BookRepository;
import com.example.bookstore.repository.CategoryRepository;
import com.example.bookstore.repository.CouponRepository;
import com.example.bookstore.repository.SellerShopRepository;
import com.example.bookstore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class PanelPageController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final SellerShopRepository sellerShopRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final CouponRepository couponRepository;

    public PanelPageController(JwtUtil jwtUtil, UserRepository userRepository,
                               SellerShopRepository sellerShopRepository,
                               BookRepository bookRepository,
                               CategoryRepository categoryRepository,
                               CouponRepository couponRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.sellerShopRepository = sellerShopRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.couponRepository = couponRepository;
    }
    @GetMapping("/seller/shop")
    public String sellerShop(Model model, Authentication authentication) {
        model.addAttribute("pageTitle", "Hồ sơ gian hàng");
        model.addAttribute("pageSubtitle", "Quản lý thông tin cửa hàng của bạn");
        model.addAttribute("activeMenu", "seller-shop");

        // Default values for layout
        model.addAttribute("sellerName", "Seller");
        model.addAttribute("sellerEmail", "seller@bookom.com");
        model.addAttribute("sellerAvatar", null);
        model.addAttribute("authUserId", null);
        model.addAttribute("authUserRole", "SELLER");
        model.addAttribute("authAccessToken", null);
        model.addAttribute("isOwner", false);
        model.addAttribute("shopSlug", null);
        model.addAttribute("shop", null);
        model.addAttribute("bookCount", 0);
        model.addAttribute("joinDuration", "—");

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            Long userId = null;

            if (principal instanceof com.example.bookstore.security.JwtAuthenticatedPrincipal jwtPrincipal) {
                userId = jwtPrincipal.userId();
            } else if (principal instanceof User user) {
                userId = user.getId();
            }

            if (userId != null) {
                model.addAttribute("authUserId", userId);
                SellerShop shop = sellerShopRepository.findBySellerId(userId).orElse(null);
                if (shop != null && shop.getSeller() != null) {
                    // Inject authentication data cho JS
                    model.addAttribute("authUserRole", "SELLER");
                    model.addAttribute("authAccessToken", null);
                    // Chủ shop -> isOwner = true
                    model.addAttribute("isOwner", true);
                    model.addAttribute("shopSlug", shop.getSlug());
                    model.addAttribute("shop", shop);
                    // Inject seller info for layout
                    String fullName = shop.getSeller().getFirstName() + " " + shop.getSeller().getLastName();
                    model.addAttribute("sellerName", fullName.trim().isEmpty() ? shop.getSeller().getUsername() : fullName.trim());
                    model.addAttribute("sellerEmail", shop.getContactEmail() != null ? shop.getContactEmail() : shop.getSeller().getEmail());
                    model.addAttribute("sellerAvatar", shop.getLogoUrl());
                }
            }
        }
        return "seller/Shop_Seller";
    }



    @GetMapping("/admin")
    public String adminDashboard(Model model) {
        model.addAttribute("pageTitle", "Tổng quan hệ thống");
        model.addAttribute("pageSubtitle", "Theo dõi hoạt động kinh doanh toàn sàn");
        model.addAttribute("activeMenu", "admin-dashboard");
        return "admin/Admin";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        model.addAttribute("pageTitle", "Quản lý người dùng");
        model.addAttribute("pageSubtitle", "Lọc theo tên, vai trò và trạng thái tài khoản");
        model.addAttribute("activeMenu", "admin-users");
        return "admin/Admin_Users";
    }

    @GetMapping("/admin/books")
    public String adminBooks(Model model) {
        model.addAttribute("pageTitle", "Kiểm duyệt sản phẩm");
        model.addAttribute("pageSubtitle", "Phê duyệt sách mới và quản lý nội dung");
        model.addAttribute("activeMenu", "admin-books");
        return "admin/Admin_Books";
    }

    @GetMapping("/admin/shops")
    public String adminShops(Model model) {
        model.addAttribute("pageTitle", "Xét duyệt gian hàng");
        model.addAttribute("pageSubtitle", "Quản lý đối tác và thông tin pháp lý");
        model.addAttribute("activeMenu", "admin-shops");
        return "admin/Admin_Shops";
    }

    @GetMapping("/admin/orders")
    public String adminOrders(Model model) {
        model.addAttribute("pageTitle", "Quản lý đơn hàng");
        model.addAttribute("pageSubtitle", "Xem toàn bộ đơn hàng trên hệ thống");
        model.addAttribute("activeMenu", "admin-orders");
        return "admin/Admin_Orders";
    }

    @GetMapping("/admin/categories")
    public String adminCategories(Model model) {
        model.addAttribute("pageTitle", "Quản lý danh mục");
        model.addAttribute("pageSubtitle", "Thêm, sửa và xóa danh mục sách trên sàn");
        model.addAttribute("activeMenu", "admin-categories");
        return "admin/Admin_Categories";
    }

    @GetMapping("/admin/coupons")
    public String adminCoupons(Model model) {
        model.addAttribute("pageTitle", "Quản lý khuyến mãi");
        model.addAttribute("pageSubtitle", "Tạo và quản lý các mã giảm giá trên toàn sàn");
        model.addAttribute("activeMenu", "admin-coupons");
        return "admin/Admin_Coupons";
    }

    @GetMapping("/admin/customers")
    public String adminCustomers(Model model) {
        model.addAttribute("pageTitle", "Phân tích khách hàng");
        model.addAttribute("pageSubtitle", "Gom cụm khách hàng & dự đoán rời bỏ bằng AI");
        model.addAttribute("activeMenu", "admin-customers");
        return "admin/Admin_Customers";
    }

    @GetMapping("/admin/seller-applications")
    public String adminSellerApplications(Model model) {
        model.addAttribute("pageTitle", "Duyệt người bán");
        model.addAttribute("pageSubtitle", "Tìm kiếm, phân trang và xử lý yêu cầu trở thành người bán");
        model.addAttribute("activeMenu", "admin-seller-applications");
        return "admin/Admin_Seller_Applications";
    }

    @GetMapping("/seller/dashboard")
    public String sellerDashboard(Model model) {
        model.addAttribute("pageTitle", "Tong quan nha ban");
        model.addAttribute("pageSubtitle", "Theo doi tong quan don hang va kho");
        model.addAttribute("activeMenu", "seller-dashboard");
        return "seller/Seller_Dashboard";
    }

    @GetMapping("/seller/orders")
    public String sellerOrders(Model model) {
        model.addAttribute("pageTitle", "Quan ly don hang");
        model.addAttribute("pageSubtitle", "Loc theo ma don va trang thai");
        model.addAttribute("activeMenu", "seller-orders");
        return "seller/Seller_Orders";
    }

    @GetMapping("/seller/inventory")
    public String sellerInventory(Model model) {
        model.addAttribute("pageTitle", "Quan ly kho hang");
        model.addAttribute("pageSubtitle", "Loc theo ten sach, danh muc, ton kho");
        model.addAttribute("activeMenu", "seller-inventory");
        return "seller/Inventory_Management";
    }

    @GetMapping("/seller/analytics")
    public String sellerAnalytics(Model model) {
        model.addAttribute("pageTitle", "Phan tich doanh thu");
        model.addAttribute("pageSubtitle", "Bieu do doanh thu va trang thai don");
        model.addAttribute("activeMenu", "seller-analytics");
        return "seller/Seller_Analytics";
    }

    @GetMapping("/seller/vouchers")
    public String sellerVouchers(Model model) {
        model.addAttribute("pageTitle", "Quan ly khuyen mai");
        model.addAttribute("pageSubtitle", "Tao va quan ly ma giam gia cho cua hang");
        model.addAttribute("activeMenu", "seller-vouchers");
        return "seller/Seller_Vouchers";
    }

    /*@GetMapping("/seller/shop")
    public String sellerShop(Model model) {
        model.addAttribute("pageTitle", "Ho so gian hang");
        model.addAttribute("pageSubtitle", "Cap nhat thong tin shop va trang thai hoat dong");
        model.addAttribute("activeMenu", "seller-shop");
        return "seller/Shop_Seller";
    }*/


    @GetMapping("/seller/product-detail")
    public String sellerProductDetail() {
        return "seller/Seller_Product_Detail";
    }

    @GetMapping("/seller/customers")
    public String sellerCustomers(Model model) {
        model.addAttribute("pageTitle", "Khách hàng của tôi");
        model.addAttribute("pageSubtitle", "Phân tích khách hàng & đề xuất giữ chân");
        model.addAttribute("activeMenu", "seller-customers");
        return "seller/Seller_Customers";
    }

    @GetMapping("/seller/chat")
    public String sellerChat(Model model, Authentication authentication) {
        model.addAttribute("pageTitle", "Tin nhan");
        model.addAttribute("pageSubtitle", "Quan ly tin nhan voi khach hang");
        model.addAttribute("activeMenu", "seller-chat");

        // Inject authentication data for seed users (dev mode)
        // This ensures the Chat page can call APIs without requiring OTP login
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            Long userId = null;
            String role = null;
            String accessToken = null;

            if (principal instanceof com.example.bookstore.security.JwtAuthenticatedPrincipal jwtPrincipal) {
                userId = jwtPrincipal.userId();
                role = jwtPrincipal.roles() != null && !jwtPrincipal.roles().isEmpty()
                    ? jwtPrincipal.roles().get(0) : null;
            } else if (principal instanceof User user) {
                userId = user.getId();
                role = user.getRole() != null ? user.getRole().name() : null;
            }

            // Generate JWT token for the authenticated user
            if (userId != null && role != null) {
                try {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        accessToken = jwtUtil.generateToken(user);
                    }
                } catch (Exception e) {
                    // Token generation failed silently - frontend will use X-User-Id fallback
                }
            }

            if (userId != null) {
                model.addAttribute("authUserId", userId);
                model.addAttribute("authUserRole", role != null ? role : "BUYER");
                model.addAttribute("authAccessToken", accessToken);
            }
        }

        return "seller/Chat_Page";
    }

    @GetMapping("/become-seller")
    public String becomeSellerPage(Model model) {
        model.addAttribute("pageTitle", "Yêu cầu trở thành người bán");
        model.addAttribute("pageSubtitle", "Gửi thông tin cửa hàng để admin xét duyệt");
        model.addAttribute("activeMenu", "become-seller");
        return "buyer/Become_Seller";
    }

}