package com.example.bookstore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Collection {@code activity_log} - TIME-SERIES collection (MongoDB 5.0+).
 *
 * <p>Khong co {@code _id} tu nghiep vu: MongoDB tu quan ly bucket theo
 * {@code ts} (timeField) va {@code userId} (metaField), TTL 180 ngay.
 * Truoc day (SQL Server) bang nay ton tai nhung KHONG co repository nao dung
 * - sau migration se phuc vu feature that: tan suat truy cap, audit.</p>
 */
@Document(collection = "activity_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    /** timeField cua time-series collection. */
    private LocalDateTime ts;

    /** metaField cua time-series collection. */
    private Long userId;

    private String sessionId;
    private String action;
    private Long bookId;
    private Map<String, Object> metadata;
    private String ip;
}
