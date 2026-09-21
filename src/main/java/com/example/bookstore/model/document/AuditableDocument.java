package com.example.bookstore.model.document;

import java.time.LocalDateTime;

/**
 * Document co field audit chung + schemaVersion (de nang cap cau truc dan dan).
 * Dung voi @EnableMongoAuditing (@CreatedDate/@LastModifiedDate) hoac set tay.
 */
public interface AuditableDocument {

    LocalDateTime getCreatedAt();

    void setCreatedAt(LocalDateTime createdAt);

    LocalDateTime getUpdatedAt();

    void setUpdatedAt(LocalDateTime updatedAt);

    Integer getSchemaVersion();

    void setSchemaVersion(Integer schemaVersion);

    /** Version cua document (de biet doc nao can migrate). */
    default int currentSchemaVersion() {
        return 1;
    }
}
