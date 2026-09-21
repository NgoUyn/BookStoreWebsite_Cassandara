package com.example.bookstore.model.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

/**
 * Collection {@code counters} - cap phat khoa chinh kieu Long cho cac document
 * (thay the IDENTITY cua SQL Server).
 *
 * <p>Moi document: {@code { _id: "orders", seq: 300600 }}.
 * Cap id bang findAndModify + $inc => ATOMIC, khong bao gio trung id khi
 * nhieu request dong thoi (an toan cho ca moi truong nhieu instance app).</p>
 */
@Document(collection = "counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Counter {

    @Id
    private String id;

    @Field("seq")
    private Long seq;

    @Field("createdAt")
    private Date createdAt;
}
