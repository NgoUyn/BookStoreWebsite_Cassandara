package com.example.bookstore.dto.backup;

import java.util.List;

/**
 * Tổng quan tình trạng sao lưu cho trang Admin.
 *
 * @param backupDir            thư mục chứa file .archive (tuyệt đối)
 * @param databaseName         database nguồn được dump (bookom)
 * @param storageType          kho ảnh đang dùng (gridfs | local)
 * @param mongodumpAvailable   có tìm thấy mongodump.exe không
 * @param mongorestoreAvailable có tìm thấy mongorestore.exe không
 * @param busy                 đang có tiến trình backup/restore chạy
 * @param totalSizeBytes       tổng dung lượng các bản sao lưu
 * @param backups              danh sách bản sao lưu (mới nhất trước)
 */
public record BackupOverview(
        String backupDir,
        String databaseName,
        String storageType,
        boolean mongodumpAvailable,
        boolean mongorestoreAvailable,
        boolean busy,
        long totalSizeBytes,
        List<BackupInfo> backups) {
}
