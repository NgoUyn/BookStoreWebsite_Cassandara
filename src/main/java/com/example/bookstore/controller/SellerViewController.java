package com.example.bookstore.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/seller")
public class SellerViewController {

    @GetMapping("/book/form")
    public String showBookForm(@RequestParam(required = false) Long id, Model model) {
        // 1. Dữ liệu xử lý form sách
        model.addAttribute("bookId", id);

        // 2. Dữ liệu hiển thị layout giao diện (Gộp từ PanelPageController sang)
        model.addAttribute("pageTitle", "Quản lý sách");
        model.addAttribute("pageSubtitle", "Thêm mới hoặc cập nhật thông tin sách");
        model.addAttribute("activeMenu", "seller-inventory");

        return "seller/Seller_Product_Detail";
    }

    @GetMapping("/shop/form")
    public String showShopForm(Model model) {
        // Dữ liệu hiển thị layout giao diện
        model.addAttribute("pageTitle", "Quản lý cửa hàng");
        model.addAttribute("pageSubtitle", "Cập nhật thông tin cửa hàng của bạn");
        model.addAttribute("activeMenu", "seller-shop");

        return "seller/Shop_Profile_Edit";
    }
}