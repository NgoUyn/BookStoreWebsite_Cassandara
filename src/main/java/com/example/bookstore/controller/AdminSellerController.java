package com.example.bookstore.controller;

import com.example.bookstore.dto.NotificationCreateRequest;
import com.example.bookstore.dto.SellerShopResponse;
import com.example.bookstore.model.SellerShop;
import com.example.bookstore.model.User;
import com.example.bookstore.model.enums.ApprovalStatus;
import com.example.bookstore.service.NotificationService;
import com.example.bookstore.service.MailService;
import com.example.bookstore.service.SellerShopService;
import com.example.bookstore.repository.SellerShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/admin/seller-shops-management")
public class AdminSellerController {

    @Autowired
    private SellerShopRepository shopRepository;

    @Autowired
    private SellerShopService sellerShopService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MailService mailService;

    private static final Logger log = LoggerFactory.getLogger(AdminSellerController.class);

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        String keyword = (q == null || q.equalsIgnoreCase("null") || q.equalsIgnoreCase("undefined")) ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<SellerShop> filtered = shopRepository.findAll()
            .stream()
            .filter(shop -> keyword.isBlank() || matchesKeyword(shop, keyword))
            .sorted((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            })
            .collect(Collectors.toList());

        int totalItems = filtered.size();
        int totalPages = (int) Math.ceil(totalItems / (double) normalizedSize);        int fromIndex = Math.min(normalizedPage * normalizedSize, totalItems);
        int toIndex = Math.min(fromIndex + normalizedSize, totalItems);

        List<Map<String, Object>> items = filtered.stream()
                .skip((long) normalizedPage * normalizedSize)
                .limit(normalizedSize)
                .map(this::toAdminRow)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", normalizedPage);
        response.put("size", normalizedSize);
        response.put("totalItems", totalItems);
        response.put("totalPages", totalPages);
        response.put("hasNext", normalizedPage + 1 < totalPages);
        response.put("hasPrev", normalizedPage > 0);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{shopId}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long shopId) {
        SellerShop shop = shopRepository.findById(shopId).orElseThrow(() -> new RuntimeException("Shop not found"));
        // Promote user and approve via service
        var result = sellerShopService.adminApproveSeller(shop.getSeller().getId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("result", result);

        // Notify seller (catch lỗi để không ảnh hưởng tới response chính)
        try {
            NotificationCreateRequest nc = new NotificationCreateRequest();
            nc.setType(com.example.bookstore.model.enums.NotificationType.SHOP_APPROVAL_UPDATED);
            nc.setTitle("Yêu cầu mở cửa hàng đã được duyệt");
            nc.setMessage("Cửa hàng của bạn đã được admin duyệt và kích hoạt.");
            nc.setPayloadJson(String.format("{\"shopId\":%d,\"status\":\"APPROVED\"}", shop.getId()));
            var notificationResp = notificationService.createNotification(null, shop.getSeller().getId(), nc);
            resp.put("notificationId", notificationResp != null ? notificationResp.getId() : null);
        } catch (Exception e) {
            log.warn("Approve OK but notification failed for shop {}: {}", shopId, e.getMessage());
            resp.put("notificationId", null);
        }

        try {
            boolean mailConfigured = mailService.isConfigured();
            boolean mailSent = mailService.sendSellerApplicationApproved(shop.getSeller(), shop);
            resp.put("mailConfigured", mailConfigured);
            resp.put("mailSent", mailSent);
        } catch (Exception e) {
            log.warn("Approve OK but mail failed for shop {}: {}", shopId, e.getMessage());
            resp.put("mailConfigured", false);
            resp.put("mailSent", false);
        }

        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{shopId}/reject")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> reject(
            @PathVariable Long shopId,
            @RequestBody(required = false) java.util.Map<String, Object> body // Đổi sang Object cho an toàn
    ) {
        SellerShop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        // Cập nhật trạng thái thành REJECTED bài bản
        shop.setApprovalStatus(ApprovalStatus.REJECTED);

        // Kiểm tra và lấy lý do an toàn
        String reason = "";
        if (body != null && body.containsKey("reason") && body.get("reason") != null) {
            reason = body.get("reason").toString().trim();
        }

        if (!reason.isBlank()) {
            shop.setRejectionReason(reason);
        } else {
            shop.setRejectionReason("Không có lý do cụ thể.");
            reason = "Không có lý do cụ thể.";
        }

        // Lưu thay đổi vào DB trước khi tạo thông báo
        shopRepository.save(shop);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "rejected");

        // Tạo Notification (catch lỗi để không ảnh hưởng tới response chính)
        try {
            NotificationCreateRequest nc = new NotificationCreateRequest();
            nc.setType(com.example.bookstore.model.enums.NotificationType.SHOP_APPROVAL_UPDATED);
            nc.setTitle("Yêu cầu mở cửa hàng bị từ chối");
            nc.setMessage("Yêu cầu của bạn bị từ chối: " + reason);

            // Sử dụng ObjectMapper hoặc Map để sinh chuỗi JSON an toàn thay vì String.format cộng chuỗi thủ công
            try {
                java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
                payloadMap.put("shopId", shop.getId());
                payloadMap.put("status", "REJECTED");
                payloadMap.put("reason", reason);
                nc.setPayloadJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payloadMap));
            } catch (Exception e) {
                nc.setPayloadJson("{\"status\":\"REJECTED\"}");
            }

            var notificationResp = notificationService.createNotification(null, shop.getSeller().getId(), nc);
            resp.put("notificationId", notificationResp != null ? notificationResp.getId() : null);
        } catch (Exception e) {
            log.warn("Reject OK but notification failed for shop {}: {}", shopId, e.getMessage());
            resp.put("notificationId", null);
        }

        try {
            boolean mailConfigured = mailService.isConfigured();
            boolean mailSent = mailService.sendSellerApplicationRejected(shop.getSeller(), shop, reason);
            resp.put("mailConfigured", mailConfigured);
            resp.put("mailSent", mailSent);
        } catch (Exception e) {
            log.warn("Reject OK but mail failed for shop {}: {}", shopId, e.getMessage());
            resp.put("mailConfigured", false);
            resp.put("mailSent", false);
        }

        return ResponseEntity.ok(resp);
    }
    @PutMapping("/{shopId}/resend-email")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> resendEmail(@PathVariable Long shopId, @RequestParam(defaultValue = "rejected") String type) {
        SellerShop shop = shopRepository.findById(shopId).orElseThrow(() -> new RuntimeException("Shop not found"));
        boolean configured = mailService.isConfigured();
        boolean sent = false;
        if ("approved".equalsIgnoreCase(type)) {
            sent = mailService.sendSellerApplicationApproved(shop.getSeller(), shop);
        } else {
            sent = mailService.sendSellerApplicationRejected(shop.getSeller(), shop, shop.getRejectionReason());
        }
        return ResponseEntity.ok(Map.of("mailConfigured", configured, "mailSent", sent));
    }

    @PutMapping("/{shopId}/resend-notification")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> resendNotification(@PathVariable Long shopId, @RequestParam(defaultValue = "rejected") String type) {
        SellerShop shop = shopRepository.findById(shopId).orElseThrow(() -> new RuntimeException("Shop not found"));
        NotificationCreateRequest nc = new NotificationCreateRequest();
        nc.setType(com.example.bookstore.model.enums.NotificationType.SHOP_APPROVAL_UPDATED);
        if ("approved".equalsIgnoreCase(type)) {
            nc.setTitle("Yêu cầu mở cửa hàng đã được duyệt");
            nc.setMessage("Cửa hàng của bạn đã được admin duyệt và kích hoạt.");
            nc.setPayloadJson(String.format("{\"shopId\":%d,\"status\":\"APPROVED\"}", shop.getId()));
        } else {
            nc.setTitle("Yêu cầu mở cửa hàng bị từ chối");
            nc.setMessage("Yêu cầu của bạn bị từ chối" + (shop.getRejectionReason() != null ? (": " + shop.getRejectionReason()) : "."));
            nc.setPayloadJson(String.format("{\"shopId\":%d,\"status\":\"REJECTED\",\"reason\":\"%s\"}", shop.getId(), shop.getRejectionReason() == null ? "" : shop.getRejectionReason().replaceAll("\"","\\\"")));
        }
        var notificationResp = notificationService.createNotification(null, shop.getSeller().getId(), nc);
        return ResponseEntity.ok(Map.of("notificationId", notificationResp != null ? notificationResp.getId() : null));
    }

    private boolean matchesKeyword(SellerShop shop, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String shopName = shop.getShopName() == null ? "" : shop.getShopName().toLowerCase(Locale.ROOT);
        String slug = shop.getSlug() == null ? "" : shop.getSlug().toLowerCase(Locale.ROOT);
        User seller = shop.getSeller();
        String username = seller != null && seller.getUsername() != null ? seller.getUsername().toLowerCase(Locale.ROOT) : "";
        String email = seller != null && seller.getEmail() != null ? seller.getEmail().toLowerCase(Locale.ROOT) : "";
        return shopName.contains(keyword) || slug.contains(keyword) || username.contains(keyword) || email.contains(keyword);
    }

    private Map<String, Object> toAdminRow(SellerShop shop) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", shop.getId());
        row.put("sellerId", shop.getSeller() != null ? shop.getSeller().getId() : null);
        row.put("sellerUsername", shop.getSeller() != null ? shop.getSeller().getUsername() : null);
        row.put("sellerEmail", shop.getSeller() != null ? shop.getSeller().getEmail() : null);
        row.put("slug", shop.getSlug());
        row.put("shopName", shop.getShopName());
        row.put("address", shop.getAddress());
        row.put("approvalStatus", shop.getApprovalStatus() != null ? shop.getApprovalStatus().name() : "PENDING");        row.put("rejectionReason", shop.getRejectionReason());
        row.put("createdAt", shop.getCreatedAt());
        row.put("updatedAt", shop.getUpdatedAt());
        return row;
    }
}
