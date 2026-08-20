package com.example.bookstore.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegacyRouteController {

    @GetMapping("/Admin/Admin.html")
    public String legacyAdminDashboard() {
        return "redirect:/admin";
    }

    @GetMapping("/Admin/Admin_Users.html")
    public String legacyAdminUsers() {
        return "redirect:/admin/users";
    }

    @GetMapping("/Admin/Admin_Books.html")
    public String legacyAdminBooks() {
        return "redirect:/admin/books";
    }

    @GetMapping("/Admin/Admin_Shops.html")
    public String legacyAdminShops() {
        return "redirect:/admin/shops";
    }

    @GetMapping("/Seller/Seller_Dashboard.html")
    public String legacySellerDashboard() {
        return "redirect:/seller/dashboard";
    }

    @GetMapping("/Seller/Seller_Orders.html")
    public String legacySellerOrders() {
        return "redirect:/seller/orders";
    }

    @GetMapping("/Seller/Inventory_Management.html")
    public String legacySellerInventory() {
        return "redirect:/seller/inventory";
    }

    @GetMapping("/Seller/Seller_Analytics.html")
    public String legacySellerAnalytics() {
        return "redirect:/seller/analytics";
    }

    @GetMapping("/Seller/Shop_Seller.html")
    public String legacySellerShop() {
        return "redirect:/seller/shop";
    }

    @GetMapping("/Seller/Seller_Product_Detail.html")
    public String legacySellerProductDetail() {
        return "redirect:/seller/product-detail";
    }

    @GetMapping("/Main/index.html")
    public String legacyMainIndex() {
        return "redirect:/";
    }

    @GetMapping("/Main/Discovery_Page.html")
    public String legacyMainDiscovery() {
        return "redirect:/main/discovery";
    }

    @GetMapping("/Main/Auth_Page.html")
    public String legacyMainAuth() {
        return "redirect:/main/auth";
    }

    @GetMapping("/Main/Cart_Page.html")
    public String legacyMainCart() {
        return "redirect:/main/cart";
    }

    @GetMapping("/Main/Contact_us.html")
    public String legacyMainContact() {
        return "redirect:/main/contact";
    }

    @GetMapping("/Main/Checkout_Page.html")
    public String legacyMainCheckout() {
        return "redirect:/main/checkout";
    }

    @GetMapping("/Main/Order_Details.html")
    public String legacyMainOrderDetails() {
        return "redirect:/main/order-details";
    }

    @GetMapping("/Main/Order_Success.html")
    public String legacyMainOrderSuccess() {
        return "redirect:/main/order-success";
    }

    @GetMapping("/Main/Search_Result.html")
    public String legacyMainSearchResult() {
        return "redirect:/main/search";
    }

    @GetMapping("/Main/Flash_Sale.html")
    public String legacyMainFlashSale() {
        return "redirect:/main/flash-sale";
    }

    @GetMapping("/Buyer/Buyer_DashBoard.html")
    public String legacyBuyerDashboard() {
        return "redirect:/buyer/dashboard";
    }
}
