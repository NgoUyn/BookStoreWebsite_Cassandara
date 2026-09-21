package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code association_rules} - MATERIALIZED VIEW cua luat ket hop
 * "mua kem" (thay the thuat toan FP-Growth viet tay bang aggregation pipeline:
 * {@code $unwind} items -> {@code $facet} dem cap -> tinh support/confidence/lift
 * -> {@code $merge} vao collection nay; xem db/mongo/03_seed_reference.js).</p>
 */
@Document(collection = "association_rules")
@CompoundIndex(name = "uq_rules_pair", def = "{'bookAId': 1, 'bookBId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationRule implements SequencedDocument {

    @Id
    private Long id;

    private Long bookAId;
    private Long bookBId;

    private Double support;
    private Double confidence;
    private Double lift;

    private Integer transactionCount;
    private Integer windowDays;

    /** TTL 30 ngay: rule cu tu dong bi xoa de job tinh lai ghi ban moi. */
    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
