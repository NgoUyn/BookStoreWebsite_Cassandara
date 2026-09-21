package com.example.bookstore.service.mongo;

import com.example.bookstore.model.document.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Cap phat khoa chinh kieu Long bang collection {@code counters}.
 *
 * <p>Vi sao khong dung ObjectId? Xem {@link com.example.bookstore.model.document.SequencedDocument}.</p>
 *
 * <p>Vi sao findAndModify? Vi no la thao tac ATOMIC tren 1 document: doc + tang + tra ve
 * trong 1 round-trip, dam bao khong trung id ke ca khi 20 request chay song song.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoSequenceService {

    private final MongoTemplate mongoTemplate;

    /**
     * Lay id ke tiep cho mot collection/counter.
     *
     * @param name ten counter (thuong la ten collection: books, orders, ...)
     * @return id moi (tang dan, duy nhat)
     */
    public Long nextId(String name) {
        Query query = Query.query(Criteria.where("_id").is(name));
        Update update = new Update()
                .inc("seq", 1L)
                .setOnInsert("createdAt", new Date());
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);

        Counter counter = mongoTemplate.findAndModify(query, update, options, Counter.class);
        if (counter == null || counter.getSeq() == null) {
            // Truong hop hiem (upsert vua tao): doc lai cho chac chan
            Counter created = mongoTemplate.findById(name, Counter.class);
            if (created != null && created.getSeq() != null) {
                return created.getSeq();
            }
            throw new IllegalStateException("Khong cap duoc id cho counter: " + name);
        }
        return counter.getSeq();
    }

    /** Gia tri counter hien tai (khong tang). */
    public Long currentSeq(String name) {
        Counter counter = mongoTemplate.findById(name, Counter.class);
        return counter == null || counter.getSeq() == null ? 0L : counter.getSeq();
    }

    /**
     * Dong bo counter theo du lieu da co (dung khi seed/import du lieu bang
     * mongoimport - luc do counter chua duoc cap nhat).
     *
     * @param name ten counter
     * @param maxExistingId id lon nhat dang co trong collection
     */
    public void syncTo(String name, long maxExistingId) {
        Update update = new Update().max("seq", maxExistingId).setOnInsert("createdAt", new Date());
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(name)), update, Counter.class);
        log.info("[MongoSequence] da dong bo counter '{}' -> {}", name, currentSeq(name));
    }
}
