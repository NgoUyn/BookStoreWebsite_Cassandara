package com.example.bookstore.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mở "đường hầm" mapping link web /images/covers/ thẳng vào ổ cứng thư mục src
        registry.addResourceHandler("/images/covers/**")
                .addResourceLocations("file:src/main/resources/static/images/covers/");

        // LEGACY: ảnh bìa do seller upload trước đây ghi vào uploads/covers/ nhưng
        // KHÔNG có handler nào phục vụ -> ảnh 404. Mapping này (cùng các URL
        // /uploads/covers/... có sẵn trong dữ liệu seed) khôi phục hiển thị.
        // Ảnh mới được lưu trong GridFS và phục vụ qua /api/files/**.
        registry.addResourceHandler("/uploads/covers/**")
                .addResourceLocations("file:uploads/covers/");

        // Phục vụ ảnh upload cho shop (logo, banner) từ thư mục uploads/shops/
        registry.addResourceHandler("/uploads/shops/**")
                .addResourceLocations("file:uploads/shops/");

        // Phục vụ ảnh tài liệu pháp lý của hồ sơ người bán (uploads/legal-docs/)
        // Ten file la UUID -> khong the doan/duyet danh sach (khong co API list).
        registry.addResourceHandler("/uploads/legal-docs/**")
                .addResourceLocations("file:uploads/legal-docs/");
    }
}