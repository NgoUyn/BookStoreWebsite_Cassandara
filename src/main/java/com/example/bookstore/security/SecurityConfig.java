package com.example.bookstore.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Kích hoạt CORS toàn cầu lấy cấu hình từ Bean bên dưới
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // --- VŨ KHÍ TẮT POPUP MẶC ĐỊNH ---
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // ---------------------------------

                // 1. QUAN TRỌNG: Stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 2. Xử lý lỗi
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            String requestURI = request.getRequestURI();
                            String acceptHeader = request.getHeader("Accept");

                            // 1. Nếu request gọi API hoặc lấy file tĩnh (.js, .css), không bao giờ được redirect HTML
                            if ((requestURI != null && requestURI.startsWith("/api/")) ||
                                    requestURI.endsWith(".js") || requestURI.endsWith(".css")) {
                                response.setContentType("application/json;charset=UTF-8");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Access Denied\"}");
                            }
                            // 2. Chỉ request tải trang giao diện HTML chính thống từ URL thanh địa chỉ mới redirect
                            else if (acceptHeader != null && acceptHeader.contains("text/html") && !requestURI.contains(".")) {
                                response.sendRedirect("/main/auth");
                            } else {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                            }
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        // Public routes
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/webjars/**", "/favicon.ico").permitAll() // mở khóa file tĩnh
                        .requestMatchers("/api/auth/register-admin").hasAuthority("ADMIN")
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shops/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").permitAll()
                        .requestMatchers("/seller", "/seller/**").permitAll()
                        .requestMatchers("/buyer", "/buyer/**").permitAll()

                        .requestMatchers("/api/admin/seeder/**").hasAuthority("ADMIN")

                        // Protected routes dựa trên Role
                        .requestMatchers("/api/seller/**").hasAuthority("SELLER")
                        .requestMatchers("/api/books/seller/**").hasAuthority("SELLER")

                        .requestMatchers("/api/panel/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/notifications/admin").hasAuthority("ADMIN")
                        .requestMatchers("/api/notifications/admin/**").hasAuthority("ADMIN")

                        // Yêu cầu đăng nhập cho Carts, Orders và Notifications
                        .requestMatchers("/api/carts/**", "/api/orders/**", "/api/notifications/**").authenticated()

                        .anyRequest().permitAll()
                )
                // 3. Đưa Filter vào đúng vị trí
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // BEAN CẤU HÌNH CORS TOÀN CẦU: Khắc phục triệt để lỗi (failed) net::ERR_FAILED
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cho phép Frontend (ví dụ localhost:3000, 5173,...) gọi API thoải mái
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Provide a PasswordEncoder bean required by services that encode/verify passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(CustomPermissionEvaluator customPermissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(customPermissionEvaluator);
        return handler;
    }
}