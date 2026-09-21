package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/** Collection {@code support_tickets} - phieu ho tro khach hang (feature cho ML). */
@Document(collection = "support_tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket implements SequencedDocument {

    @Id
    private Long id;

    private Long userId;
    private String subject;
    private String description;

    /** OPEN | IN_PROGRESS | RESOLVED | CLOSED */
    private String status;

    /** LOW | NORMAL | HIGH | URGENT */
    private String priority;

    @Field("createdAt")
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
