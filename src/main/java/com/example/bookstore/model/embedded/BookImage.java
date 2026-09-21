package com.example.bookstore.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anh cua sach - EMBED trong {@code books.images{}}.
 * File that duoc luu bang GridFS (xem MongoConfig.gridFsTemplate), field
 * {@code coverFileId} giu ObjectId cua file trong fs.files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookImage {

    private String thumbnail;
    private String medium;
    private String large;
}
