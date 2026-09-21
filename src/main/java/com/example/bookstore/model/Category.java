package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Collection {@code categories} - danh muc sach (collection THAM CHIEU).
 *
 * <p>books luu {@code categoryId} + {@code categoryName} (denormalized) nen
 * khong can $lookup khi hien thi; {@code parentId}/{@code path} phuc vu
 * {@code $graphLookup} cho cay danh muc.</p>
 */
@Document(collection = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements SequencedDocument {

    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String slug;
    private String description;

    /** Danh muc cha (null = danh muc goc). */
    private Long parentId;

    /** Duong dan tu goc den nut hien tai: [1, 7, 19]. */
    @Builder.Default
    private List<Long> path = new ArrayList<>();

    private Integer bookCount;
    private Integer sortOrder;
    private Boolean isActive;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
