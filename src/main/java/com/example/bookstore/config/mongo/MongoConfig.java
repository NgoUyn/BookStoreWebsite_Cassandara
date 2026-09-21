package com.example.bookstore.config.mongo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * Cau hinh MongoDB cho BOOKOM.
 *
 * <p>Gom 3 phan quan trong (tuong ung tieu chi 4 - ket noi CSDL voi ung dung):</p>
 * <ol>
 *   <li>{@code spring.data.mongodb.uri} trong application.properties:
 *       uri + connection pool + database.</li>
 *   <li>{@link MongoTransactionManager}: cho phep dung {@code @Transactional}
 *       (multi-document transaction). YEU CAU replica set - xem
 *       {@code tools/mongo-dev-start.bat}.</li>
 *   <li>{@link GridFsTemplate}: luu file (anh bia, avatar, attachment) trong
 *       MongoDB thay cho thu muc uploads/ (backup 1 lenh la co ca file).</li>
 * </ol>
 *
 * <p>Do audit (createdAt/updatedAt) duoc bat bang {@code @EnableMongoAuditing}
 * + {@code @CreatedDate}/{@code @LastModifiedDate} tren document.</p>
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    public GridFsTemplate gridFsTemplate(MongoDatabaseFactory databaseFactory, MongoConverter converter) {
        return new GridFsTemplate(databaseFactory, converter);
    }
}
