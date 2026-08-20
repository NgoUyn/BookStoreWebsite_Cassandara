package com.example.bookstore.security;

import com.example.bookstore.model.User;
import com.example.bookstore.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String xUserIdHeader = request.getHeader("X-User-Id");
        
        Long userId = null;
        List<String> roles = new ArrayList<>();
        Long sellerId = null;

        // 1. Try to extract from JWT Token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                userId = jwtTokenProvider.extractUserId(token);
                roles = jwtTokenProvider.extractRoles(token);
                sellerId = jwtTokenProvider.extractSellerId(token);
                
                if (userId != null) {
                    logger.debug("Authenticated via JWT: " + userId + " with roles " + roles);
                }
            } catch (Exception e) {
                logger.error("Invalid JWT token: " + e.getMessage());
            }
        }

        // 2. Fallback to X-User-Id (Dev/Fallback Mode) if token is missing or failed
        if (userId == null && xUserIdHeader != null && !xUserIdHeader.isBlank()) {
            try {
                Long id = Long.parseLong(xUserIdHeader);
                User user = userRepository.findById(id).orElse(null);
                if (user != null && user.isActive()) {
                    userId = user.getId();
                    roles = List.of(user.getRole().name());
                    sellerId = "SELLER".equalsIgnoreCase(user.getRole().name()) ? user.getId() : null;
                    logger.info("Authenticated via X-User-Id fallback: " + userId + " (Role: " + roles.get(0) + ")");
                }
            } catch (Exception e) {
                logger.warn("Failed to authenticate via X-User-Id: " + xUserIdHeader);
            }
        }

        // 3. Set Security Context
        if (userId != null) {
            JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(userId, roles, sellerId);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = mapAuthorities(principal.roles());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } else if (authHeader != null || xUserIdHeader != null) {
            logger.warn("Request to " + request.getRequestURI() + " had auth headers but failed authentication");
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> mapAuthorities(List<String> roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (roles == null) return authorities;
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
        }
        return authorities;
    }
}