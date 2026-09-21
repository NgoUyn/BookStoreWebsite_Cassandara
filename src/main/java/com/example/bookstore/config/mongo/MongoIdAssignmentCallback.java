package com.example.bookstore.config.mongo;

import com.example.bookstore.model.document.SequencedDocument;
import com.example.bookstore.service.mongo.MongoSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Tu dong cap khoa chinh Long cho MOI document truoc khi ghi xuong MongoDB.
 *
 * <p>Nho callback nay, tang service khong phai sua: van goi
 * {@code repository.save(entity)} voi id = null nhu khi dung JPA.</p>
 *
 * <p>Chay TRUOC khi converter ghi document, nen khong bao gio gap loi
 * "Cannot autogenerate id of type java.lang.Long".</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mongo.id-assignment.enabled", havingValue = "true", matchIfMissing = true)
public class MongoIdAssignmentCallback implements BeforeConvertCallback<Object> {

    private final MongoSequenceService sequenceService;

    @Value("${app.mongo.id-assignment.log:false}")
    private boolean logAssignment;

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (entity instanceof SequencedDocument document) {
            if (document.getId() == null) {
                String sequenceName = document.sequenceName() != null
                        ? document.sequenceName()
                        : collection;
                Long id = sequenceService.nextId(sequenceName);
                document.setId(id);
                if (logAssignment) {
                    log.debug("[MongoId] cap id={} cho collection={}", id, collection);
                }
            }
        }
        return entity;
    }
}
