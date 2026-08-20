package com.example.bookstore.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
    // chỉ cho phép 1 số thẻ HTML cơ bản, chặn <script> và các thẻ nguy hiểm khác
    private static final PolicyFactory SANITIZER_POLICY = new HtmlPolicyBuilder()
            .allowElements("b", "i", "u", "em", "strong", "p", "br", "ul", "ol", "li")
            .toFactory();

    public String sanitize(String input){
        if (input == null) return null;
        return SANITIZER_POLICY.sanitize(input);
    }
}