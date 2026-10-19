package com.example.bookstore.dto.backup;

/**
 * Kết quả 1 thao tác sao lưu / phục hồi (dùng cho cả 2 chiều).
 *
 * @param success    thao tác thành công hay không
 * @param action     {@code BACKUP} | {@code RESTORE} | {@code DELETE}
 * @param fileName   file liên quan (null nếu thất bại trước khi tạo được file)
 * @param sizeBytes  dung lượng file sau thao tác
 * @param durationMs thời gian chạy tiến trình (ms)
 * @param message    thông báo cho người dùng (tiếng Việt)
 * @param log        output của mongodump/mongorestore (để admin chẩn đoán)
 */
public record BackupOperationResult(
        boolean success,
        String action,
        String fileName,
        long sizeBytes,
        long durationMs,
        String message,
        String log) {

    public static BackupOperationResult failure(String action, String message, String log) {
        return new BackupOperationResult(false, action, null, 0L, 0L, message, log);
    }
}
