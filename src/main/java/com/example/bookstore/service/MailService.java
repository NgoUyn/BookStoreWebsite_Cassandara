package com.example.bookstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String mailHost;
    private final String mailUsername;

    public MailService(
        JavaMailSender mailSender,
        @Value("${app.mail.from:${spring.mail.username:no-reply@bookstore.local}}") String fromAddress,
        @Value("${spring.mail.host:}") String mailHost,
        @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
    }

    public boolean isConfigured() {
        return !mailHost.isBlank() && !mailUsername.isBlank();
    }

    public void sendOtpEmail(String toEmail, String otp, long expireMinutes) {
        if (!isConfigured()) {
            // fallback: write to local log for dev convenience
            log.warn("Mail server not configured — OTP email not sent to {}. Writing to local fallback log.", toEmail);
            writeFallbackEmail("OTP", toEmail, buildOtpBody(otp, expireMinutes));
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(fromAddress);
        message.setSubject("[BookStore] Ma OTP xac thuc dang ky");
        message.setText(buildOtpBody(otp, expireMinutes));
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
            writeFallbackEmail("OTP", toEmail, buildOtpBody(otp, expireMinutes));
            throw e;
        }
    }

    public boolean sendSellerApplicationApproved(User user, SellerShop shop) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send approval email: user/email missing");
            return false;
        }
        if (!isConfigured()) {
            log.warn("Mail server not configured — skipping SMTP send for approved notification to {}. Writing to local fallback log.", user.getEmail());
            writeFallbackEmail("SellerApproved", user.getEmail(), buildApprovalBody(user, shop));
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(fromAddress);
        message.setSubject("[BookStore] Yêu cầu trở thành người bán đã được duyệt");
        message.setText(buildApprovalBody(user, shop));
        try {
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send approval email to {}: {}", user.getEmail(), e.getMessage(), e);
            writeFallbackEmail("SellerApproved", user.getEmail(), buildApprovalBody(user, shop));
            return false;
        }
    }

    public boolean sendSellerApplicationRejected(User user, SellerShop shop, String reason) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send rejection email: user/email missing");
            return false;
        }
        if (!isConfigured()) {
            log.warn("Mail server not configured — skipping SMTP send for rejected notification to {}. Writing to local fallback log.", user.getEmail());
            writeFallbackEmail("SellerRejected", user.getEmail(), buildRejectionBody(user, shop, reason));
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(fromAddress);
        message.setSubject("[BookStore] Yêu cầu trở thành người bán chưa được duyệt");
        message.setText(buildRejectionBody(user, shop, reason));
        try {
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Failed to send rejection email to {}: {}", user.getEmail(), e.getMessage(), e);
            writeFallbackEmail("SellerRejected", user.getEmail(), buildRejectionBody(user, shop, reason));
            return false;
        }
    }

    private void writeFallbackEmail(String tag, String toEmail, String body) {
        try {
            java.nio.file.Path logsDir = java.nio.file.Paths.get("logs");
            if (!java.nio.file.Files.exists(logsDir)) java.nio.file.Files.createDirectories(logsDir);
            java.nio.file.Path out = logsDir.resolve("outgoing-emails.log");
            String entry = "---\n" + java.time.OffsetDateTime.now().toString() + "\n" + "Tag: " + tag + "\nTo: " + toEmail + "\n" + body + "\n";
            java.nio.file.Files.writeString(out, entry, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.error("Failed to write fallback email to local log: {}", ex.getMessage(), ex);
        }
    }

    private String buildOtpBody(String otp, long expireMinutes) {
        return "Xin chao,\n\n"
            + "Ma OTP xac thuc dang ky BookStore cua ban la: " + otp + "\n"
            + "Ma co hieu luc trong " + expireMinutes + " phut.\n\n"
            + "Neu ban khong yeu cau thao tac nay, vui long bo qua email nay.\n\n"
            + "BookStore Team";
    }

    private String buildApprovalBody(User user, SellerShop shop) {
        return "Xin chao " + safeName(user) + ",\n\n"
            + "Yeu cau tro thanh nguoi ban cua ban da duoc admin duyet.\n"
            + "Ten gian hang: " + safeText(shop != null ? shop.getShopName() : null) + "\n"
            + "Slug: " + safeText(shop != null ? shop.getSlug() : null) + "\n\n"
            + "Ban co the dang nhap lai va su dung cac chuc nang danh cho nguoi ban.\n\n"
            + "BookStore Team";
    }

    private String buildRejectionBody(User user, SellerShop shop, String reason) {
        return "Xin chao " + safeName(user) + ",\n\n"
            + "Yeu cau tro thanh nguoi ban cua ban chua duoc duyet.\n"
            + "Ten gian hang: " + safeText(shop != null ? shop.getShopName() : null) + "\n"
            + "Ly do: " + safeText(reason) + "\n\n"
            + "Ban co the chinh sua thong tin va gui lai yeu cau sau.\n\n"
            + "BookStore Team";
    }

    private String safeName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName();
        }
        return safeText(user.getUsername());
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "(khong co)" : text;
    }
}
