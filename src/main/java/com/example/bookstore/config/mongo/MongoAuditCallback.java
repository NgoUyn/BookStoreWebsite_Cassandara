package com.example.bookstore.config.mongo;

import com.example.bookstore.model.document.AuditableDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tu dong dien {@code createdAt / updatedAt / schemaVersion} cho MOI document
 * implements {@link AuditableDocument} truoc khi ghi xuong MongoDB.
 *
 * <p><b>Vi sao can:</b> {@code @EnableMongoAuditing} chi hoat dong khi field co
 * {@code @CreatedDate}/{@code @LastModifiedDate}; cac model cua do an giu field
 * audit tay (de tuong thich ten field voi du lieu da seed), nen neu khong co
 * callback nay thi {@code createdAt} = null va MongoDB se tu choi insert voi
 * loi {@code code=121 Document failed validation} (validator cua collection
 * {@code users/books/orders...} bat buoc co {@code createdAt}).</p>
 *
 * <p>Callback nay khong tiem bean nao (khong the tao vong lap dependency voi
 * {@code MappingMongoConverter} nhu {@code MongoIdAssignmentCallback}).</p>
 */
@Slf4j
@Component
public class MongoAuditCallback implements BeforeConvertCallback<Object> {

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (entity instanceof AuditableDocument document) {
            LocalDateTime now = LocalDateTime.now();
            if (document.getCreatedAt() == null) {
                document.setCreatedAt(now);
            }
            document.setUpdatedAt(now);
            if (document.getSchemaVersion() == null) {
                document.setSchemaVersion(document.currentSchemaVersion());
            }
        }
        return entity;
    }
}
