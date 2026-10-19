package com.example.bookstore.service.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Tìm đường dẫn các exe của MongoDB: {@code mongodump}/{@code mongorestore}
 * (MongoDB Database Tools) và {@code mongosh}.
 *
 * <p>Cùng logic với {@code MongoDevBootstrap.resolveMongoTool} nhưng tách riêng
 * để lấy được từ Spring context (bootstrap chạy TRƯỚC khi app khởi động nên
 * không thể autowire) — nhờ vậy module backup không phải sửa class bootstrap.</p>
 *
 * <p>Thứ tự tìm: {@code app.backup.tools-bin} → {@code app.mongo.autostart.mongo-tools-bin}
 * → {@code C:\Program Files\MongoDB\Tools\<version>\bin} (bản mới nhất) → PATH.</p>
 */
@Service
public class MongoToolsLocator {

    private static final Logger log = LoggerFactory.getLogger(MongoToolsLocator.class);

    private static final String TOOLS_ROOT = "C:\\Program Files\\MongoDB\\Tools";

    @Value("${app.mongo.autostart.mongo-tools-bin:}")
    private String mongoToolsBin;

    @Value("${app.mongo.autostart.mongosh-exe:}")
    private String mongoshExe;

    /** Cho phép chỉ định riêng thư mục chứa mongodump/mongorestore. */
    @Value("${app.backup.tools-bin:}")
    private String backupToolsBin;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Đường dẫn exe, hoặc null nếu không tìm thấy. */
    public String findTool(String tool) {
        String cached = cache.get(tool);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveTool(tool);
        if (resolved != null) {
            cache.put(tool, resolved);
            log.debug("MongoDB tool '{}' -> {}", tool, resolved);
        }
        return resolved;
    }

    public boolean isAvailable(String tool) {
        return findTool(tool) != null;
    }

    /** mongosh.exe (dùng cho script .bat / xác minh thủ công). */
    public String findMongosh() {
        String cached = cache.get("mongosh");
        if (cached != null) {
            return cached;
        }
        String resolved = resolveMongosh();
        if (resolved != null) {
            cache.put("mongosh", resolved);
        }
        return resolved;
    }

    /** Xoá cache — dùng khi admin vừa cài Database Tools mà không muốn restart app. */
    public void clearCache() {
        cache.clear();
    }

    // ------------------------------------------------------------------

    private String resolveTool(String tool) {
        for (String bin : new String[] { backupToolsBin, mongoToolsBin }) {
            if (bin != null && !bin.isBlank()) {
                Path candidate = Path.of(bin.trim(), tool + ".exe");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }

        Path toolsRoot = Path.of(TOOLS_ROOT);
        if (Files.isDirectory(toolsRoot)) {
            try (Stream<Path> dirs = Files.list(toolsRoot)) {
                Path found = dirs.filter(Files::isDirectory)
                        .map(dir -> dir.resolve("bin").resolve(tool + ".exe"))
                        .filter(Files::isRegularFile)
                        .max(Comparator.comparing(path -> path.getParent().getParent().getFileName().toString()))
                        .orElse(null);
                if (found != null) {
                    return found.toString();
                }
            } catch (IOException e) {
                log.debug("Không đọc được {}: {}", TOOLS_ROOT, e.getMessage());
            }
        }
        return onPath(tool);
    }

    private String resolveMongosh() {
        if (mongoshExe != null && !mongoshExe.isBlank() && Files.isRegularFile(Path.of(mongoshExe.trim()))) {
            return mongoshExe.trim();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            Path candidate = Path.of(localAppData, "Programs", "mongosh", "mongosh.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return onPath("mongosh");
    }

    private static String onPath(String exe) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(dir, exe + ".exe");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            } catch (RuntimeException e) {
                // entry PATH không hợp lệ - bỏ qua
            }
        }
        return null;
    }
}
