package com.example.bookstore.service.mongo;

import com.example.bookstore.dto.backup.BackupInfo;
import com.example.bookstore.dto.backup.BackupOperationResult;
import com.example.bookstore.dto.backup.BackupOverview;
import com.example.bookstore.service.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Sao lưu / phục hồi TOÀN BỘ hệ thống BOOKOM bằng MongoDB Database Tools.
 *
 * <p>Vì ảnh (bìa sách, logo/banner shop, tài liệu pháp lý) được lưu trong GridFS
 * của chính database {@code bookom} (xem {@code app.storage.type=gridfs}), một lệnh
 * {@code mongodump --gzip --archive} là sao lưu được CẢ dữ liệu LẪN ảnh — file
 * {@code backups/bookom_yyyyMMdd_HHmmss.archive} mang đi máy khác restore là chạy được.</p>
 *
 * <p>An toàn:</p>
 * <ul>
 *   <li>Tên file được kiểm tra bằng regex + {@code normalize()} chống path traversal.</li>
 *   <li>Chỉ chạy 1 tiến trình tại một thời điểm ({@link AtomicBoolean}) — tránh
 *       dump/restore chồng nhau làm hỏng dữ liệu.</li>
 *   <li>Có timeout ({@code app.backup.timeout-seconds}) và log output của tool
 *       để admin tự chẩn đoán khi thất bại.</li>
 * </ul>
 *
 * <p>LUÔN có thể sao lưu bằng dòng lệnh tương đương:
 * {@code tools\mongo-backup.bat} / {@code tools\mongo-restore.bat <file>}.</p>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final String ARCHIVE_SUFFIX = ".archive";
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,120}\\.archive$");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_LOG_CHARS = 4000;

    private final MongoToolsLocator toolsLocator;
    private final FileStorageService fileStorageService;

    @Value("${spring.data.mongodb.uri:mongodb://127.0.0.1:27018/bookom?replicaSet=rs0}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:bookom}")
    private String databaseName;

    @Value("${app.backup.dir:backups}")
    private String backupDir;

    @Value("${app.backup.timeout-seconds:900}")
    private long timeoutSeconds;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public BackupService(MongoToolsLocator toolsLocator, FileStorageService fileStorageService) {
        this.toolsLocator = toolsLocator;
        this.fileStorageService = fileStorageService;
    }

    public boolean isBusy() {
        return running.get();
    }

    public Path backupDirectory() {
        return Paths.get(backupDir).toAbsolutePath().normalize();
    }

    /** Tổng quan cho trang Admin: thư mục, công cụ, danh sách bản sao lưu. */
    public BackupOverview overview() {
        List<BackupInfo> backups = listBackups();
        long total = backups.stream().mapToLong(BackupInfo::sizeBytes).sum();
        return new BackupOverview(
                backupDirectory().toString(),
                databaseName,
                fileStorageService.backend(),
                toolsLocator.isAvailable("mongodump"),
                toolsLocator.isAvailable("mongorestore"),
                isBusy(),
                total,
                backups);
    }

    /** Danh sách file .archive trong {@code app.backup.dir} (mới nhất trước). */
    public List<BackupInfo> listBackups() {
        Path dir = backupDirectory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> archives = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(ARCHIVE_SUFFIX))
                    .sorted(Comparator.comparing((Path path) -> lastModified(path)).reversed())
                    .toList();

            List<BackupInfo> result = new ArrayList<>();
            boolean latest = true;
            for (Path archive : archives) {
                result.add(new BackupInfo(
                        archive.getFileName().toString(),
                        sizeOf(archive),
                        lastModified(archive),
                        latest));
                latest = false;
            }
            return result;
        } catch (IOException e) {
            log.warn("Không đọc được thư mục backup {}: {}", dir, e.getMessage());
            return List.of();
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static LocalDateTime lastModified(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    // ==================================================================
    // SAO LƯU
    // ==================================================================

    /**
     * Chạy {@code mongodump --uri=<uri> --archive=<file> --gzip}.
     * Archive chứa cả dữ liệu lẫn ảnh GridFS (fs.files + fs.chunks).
     */
    public BackupOperationResult createBackup() {
        if (!running.compareAndSet(false, true)) {
            return BackupOperationResult.failure("BACKUP",
                    "Đang có tiến trình sao lưu/phục hồi khác chạy. Vui lòng đợi hoàn tất.", null);
        }

        long startedAt = System.currentTimeMillis();
        try {
            String mongodump = toolsLocator.findTool("mongodump");
            if (mongodump == null) {
                return BackupOperationResult.failure("BACKUP",
                        "Không tìm thấy mongodump.exe (MongoDB Database Tools). "
                                + "Cài bằng: winget install --id MongoDB.DatabaseTools -e", null);
            }

            Files.createDirectories(backupDirectory());
            Path target = backupDirectory()
                    .resolve(databaseName + "_" + LocalDateTime.now().format(FILE_STAMP) + ARCHIVE_SUFFIX);

            CommandResult command = run(List.of(
                    mongodump,
                    "--uri=" + mongoUri,
                    "--archive=" + target,
                    "--gzip"));

            long duration = System.currentTimeMillis() - startedAt;
            long size = sizeOf(target);
            boolean success = command.exitCode() == 0 && size > 0;

            String message = success
                    ? "Sao lưu thành công: " + target.getFileName() + " (" + humanSize(size) + ")"
                    : "Sao lưu thất bại (exit=" + command.exitCode() + "). Xem log để biết chi tiết.";
            log.info("BACKUP {} -> {} ({} bytes, {} ms)", databaseName, target.getFileName(), size, duration);

            return new BackupOperationResult(success, "BACKUP", target.getFileName().toString(),
                    size, duration, message, command.output());

        } catch (IOException | RuntimeException e) {
            log.error("Sao lưu thất bại", e);
            return BackupOperationResult.failure("BACKUP", "Sao lưu thất bại: " + e.getMessage(), null);
        } finally {
            running.set(false);
        }
    }

    // ==================================================================
    // PHỤC HỒI
    // ==================================================================

    /**
     * Chạy {@code mongorestore --uri=<uri> --archive=<file> --gzip --drop}.
     *
     * <p>{@code --drop} xoá collection trước khi nạp lại => trạng thái sau restore
     * đúng bằng trạng thái lúc dump (không trộn dữ liệu cũ với dữ liệu mới).</p>
     */
    public BackupOperationResult restoreBackup(String fileName) {
        if (!running.compareAndSet(false, true)) {
            return BackupOperationResult.failure("RESTORE",
                    "Đang có tiến trình sao lưu/phục hồi khác chạy. Vui lòng đợi hoàn tất.", null);
        }

        long startedAt = System.currentTimeMillis();
        try {
            String mongorestore = toolsLocator.findTool("mongorestore");
            if (mongorestore == null) {
                return BackupOperationResult.failure("RESTORE",
                        "Không tìm thấy mongorestore.exe (MongoDB Database Tools). "
                                + "Cài bằng: winget install --id MongoDB.DatabaseTools -e", null);
            }

            Path archive = resolveBackupPath(fileName);
            if (!Files.isRegularFile(archive)) {
                return BackupOperationResult.failure("RESTORE",
                        "Không tìm thấy file sao lưu: " + fileName, null);
            }

            CommandResult command = run(List.of(
                    mongorestore,
                    "--uri=" + mongoUri,
                    "--archive=" + archive,
                    "--gzip",
                    "--drop"));

            long duration = System.currentTimeMillis() - startedAt;
            boolean success = command.exitCode() == 0;
            String message = success
                    ? "Phục hồi thành công từ " + fileName + " (dữ liệu hiện tại đã bị ghi đè bằng bản sao lưu)."
                    : "Phục hồi thất bại (exit=" + command.exitCode() + "). Xem log để biết chi tiết.";
            log.warn("RESTORE từ {} -> {} ({} ms)", fileName, success ? "OK" : "THẤT BẠI", duration);

            return new BackupOperationResult(success, "RESTORE", fileName,
                    sizeOf(archive), duration, message, command.output());

        } catch (IllegalArgumentException e) {
            return BackupOperationResult.failure("RESTORE", e.getMessage(), null);
        } catch (IOException | RuntimeException e) {
            log.error("Phục hồi thất bại", e);
            return BackupOperationResult.failure("RESTORE", "Phục hồi thất bại: " + e.getMessage(), null);
        } finally {
            running.set(false);
        }
    }

    /** Xoá 1 file sao lưu (không ảnh hưởng database). */
    public BackupOperationResult deleteBackup(String fileName) {
        try {
            Path archive = resolveBackupPath(fileName);
            boolean deleted = Files.deleteIfExists(archive);
            return new BackupOperationResult(deleted, "DELETE", fileName, 0L, 0L,
                    deleted ? "Đã xoá bản sao lưu " + fileName : "Không tìm thấy file " + fileName, null);
        } catch (IllegalArgumentException e) {
            return BackupOperationResult.failure("DELETE", e.getMessage(), null);
        } catch (IOException e) {
            return BackupOperationResult.failure("DELETE", "Không xoá được file: " + e.getMessage(), null);
        }
    }
    // ==================================================================
    // TIỆN ÍCH
    // ==================================================================

    /**
     * Kiểm tra tên file và trả về đường dẫn tuyệt đối bên trong thư mục backup.
     * Chặn path traversal kiểu {@code ..\..\windows\system32\cmd.exe}.
     */
    public Path resolveBackupPath(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        if (!SAFE_FILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        }
        Path dir = backupDirectory();
        Path resolved = dir.resolve(name).normalize();
        if (!resolved.startsWith(dir) || resolved.getParent() == null || !resolved.getParent().equals(dir)) {
            throw new IllegalArgumentException("Tên file không hợp lệ: " + fileName);
        }
        return resolved;
    }

    /** Chạy tiến trình, gộp stdout+stderr, có timeout để không treo thread vô hạn. */
    private CommandResult run(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append('\n');
                    }
                }
            } catch (IOException e) {
                // tiến trình kết thúc / stream đóng - không phải lỗi nghiêm trọng
            }
        }, "bookom-backup-reader");
        reader.setDaemon(true);
        reader.start();

        int exitCode;
        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (finished) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                exitCode = -1;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            exitCode = -1;
        }

        try {
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String text;
        synchronized (output) {
            text = output.toString().trim();
        }
        if (text.length() > MAX_LOG_CHARS) {
            text = text.substring(0, MAX_LOG_CHARS) + "\n... (đã cắt bớt log)";
        }
        return new CommandResult(exitCode, text);
    }

    /** Đổi byte sang chuỗi dễ đọc cho thông báo trên giao diện. */
    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** exit code + output của mongodump/mongorestore. */
    private record CommandResult(int exitCode, String output) {
    }


}
