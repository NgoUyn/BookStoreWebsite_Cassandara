package com.example.bookstore.model;

import com.example.bookstore.model.document.AuditableDocument;
import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.model.embedded.UserSnapshot;
import com.example.bookstore.model.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AGGREGATE ROOT #5 - collection {@code seller_shops}.
 *
 * <p>EMBED: seller snapshot (de hien thi ten/email/avatar chu shop),
 * location (GeoJSON Point - index 2dsphere cho $geoNear), stats (doanh thu
 * 30 ngay - tinh san bang $merge).</p>
 */
@Document(collection = "seller_shops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerShop implements SequencedDocument, AuditableDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private Long sellerId;

    @Indexed(unique = true)
    private String slug;

    private String shopName;
    private String description;

    private UserSnapshot seller;

    private String logoUrl;
    private String bannerUrl;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String city;
    private String province;

    /** Toa do GeoJSON: {"type":"Point","coordinates":[lng,lat]}. */
    @GeoSpatialIndexed(name = "geo_shops_location")
    private Location location;

    private String rejectionReason;
    private ApprovalStatus approvalStatus;
    private Integer followerCount;
    private Double rating;
    private Integer ratingCount;

    private ShopStats stats;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    private Integer schemaVersion;

    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        @Builder.Default
        private String type = "Point";
        private List<Double> coordinates;
    }

    // ======================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopStats {
        private Integer productCount;
        private Integer orderCount30d;
        private Double revenue30d;
        private Double responseRate;
        private Integer unitsSold;
        private Integer shippedOrders;
    }
}
