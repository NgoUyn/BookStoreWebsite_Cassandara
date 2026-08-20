package com.example.bookstore.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthPageController {

    @GetMapping("/login")
    public String login() {
        // Redirect to existing static auth page to avoid 404 when frontend redirects to /login
        return "redirect:/Main/Auth_Page.html";
    }
}
