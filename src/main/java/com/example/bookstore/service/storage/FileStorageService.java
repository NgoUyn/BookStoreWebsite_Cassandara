package com.example.bookstore.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Lớp trừu tượng cho việc lưu ảnh của BOOKOM (ảnh bìa sách, logo/banner shop,
 * ảnh tài liệu pháp lý).
 *
 * <p>2 hiện thực (chọn bằng {@code app.storage.type}):</p>
 * <ul>
 *   <li>{@link GridFsStorageService} — {@code gridfs} (mặc định): file nằm trong
 *       {@code fs.files}/{@code fs.chunks} của chính database → {@code mongodump}
 *       là sao lưu được CẢ dữ liệu lẫn ảnh trong 1 file archive.</li>
 *   <li>{@link LocalFileStorageService} — {@code local}: giữ nguyên hành vi cũ
 *       (ghi vào {@code uploads/}, phục vụ bởi {@code WebConfig}).</li>
 * </ul>
 */
public interface FileStorageService {

    /** File được lưu trong GridFS của MongoDB. */
    String GRIDFS = "gridfs";

    /** File được lưu trên ổ đĩa (thư mục uploads/). */
    String LOCAL = "local";

    /** Backend đang hoạt động: {@link #GRIDFS} hoặc {@link #LOCAL}. */
    String backend();

    /**
     * Kiểm tra chữ ký file bằng Apache Tika rồi lưu vào kho.
     *
     * @param file  file upload từ client
     * @param scope phạm vi sử dụng: {@code covers} | {@code shops} | {@code legal-docs}
     * @throws IllegalArgumentException khi file rỗng hoặc không phải ảnh JPG/PNG/WEBP
     * @throws IOException khi ghi file thất bại
     */
    StoredFile store(MultipartFile file, String scope) throws IOException;

    /**
     * Mở stream nội dung của file để phục vụ download.
     *
     * @throws IllegalArgumentException khi id không hợp lệ
     * @throws java.util.NoSuchElementException khi không tìm thấy file
     */
    FileDownload open(String fileId) throws IOException;

    /** Xoá file (không ném lỗi nếu file đã không còn). */
    void delete(String fileId);
}
