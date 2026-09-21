package com.example.bookstore.config.mongo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Cau hinh cho {@link MongoDevBootstrap}.
 *
 * <p>Thu tu uu tien khi doc gia tri (cao -> thap):</p>
 * <ol>
 *   <li>command line: {@code --app.mongo.autostart.port=27018}</li>
 *   <li>system property: {@code -Dapp.mongo.autostart.port=27018}</li>
 *   <li>bien moi truong: {@code APP_MONGO_AUTOSTART_PORT=27018}</li>
 *   <li>{@code src/main/resources/application.properties} (classpath)</li>
 *   <li>gia tri mac dinh trong class nay</li>
 * </ol>
 *
 * <p>Luu y: lop nay KHONG dung Spring {@code Environment} vi duoc goi tu
 * {@code BookStoreApplication.main(String[])} TRUOC khi context khoi dong.
 * Gia tri co placeholder {@code ${...}} trong application.properties se bi bo qua
 * (dung gia tri mac dinh) - chi ho tro gia tri literal.</p>
 */
public record MongoDevBootstrapSettings(
        boolean enabled,
        Mode mode,
        String host,
        int port,
        String replicaSet,
        String database,
        String dbPath,
        String mongodExe,
        String mongoshExe,
        String mongoToolsBin,
        String composeFile,
        String composeService,
        int waitSeconds,
        boolean seedEnabled,
        boolean seedBooks,
        boolean failFast,
        Path projectDir) {

    /** Cach khoi dong MongoDB khi cong chua mo. */
    public enum Mode {
        /** Uu tien mongod.exe cua may, neu khong co thi dung docker compose. */
        AUTO,
        /** Chi dung mongod.exe (nhu tools\mongo-dev-start.bat). */
        NATIVE,
        /** Chi dung docker compose (docker-compose.mongo.yml). */
        DOCKER
    }

    private static final String PREFIX = "app.mongo.autostart.";
    private static final Properties CLASSPATH_PROPERTIES = loadClasspathProperties();

    /** URI ket noi day du (dung cho mongoimport / mongosh --uri). */
    public String uri() {
        return "mongodb://" + host + ":" + port + "/" + database + "?replicaSet=" + replicaSet;
    }

    /** Duong dan script trong db/mongo (vd: 00_init_replica_set.js). */
    public Path dbScript(String fileName) {
        return projectDir.resolve("db").resolve("mongo").resolve(fileName);
    }

    /** Duong dan file log cua mongod dev instance. */
    public Path mongodLog() {
        return Path.of(dbPath).resolve("mongod.log");
    }

    public static MongoDevBootstrapSettings load(String[] args) {
        String projectDir = raw("project-dir", args);
        Path dir = (projectDir == null || projectDir.isBlank())
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(projectDir);

        String composeFile = raw("compose-file", args);
        Path compose = (composeFile == null || composeFile.isBlank())
                ? dir.resolve("docker-compose.mongo.yml")
                : dir.resolve(composeFile);

        return new MongoDevBootstrapSettings(
                bool("enabled", args, true),
                parseMode(raw("mode", args)),
                orDefault(raw("host", args), "127.0.0.1"),
                intValue("port", args, 27018),
                orDefault(raw("replica-set", args), "rs0"),
                orDefault(raw("database", args), "bookom"),
                windowsPath(orDefault(raw("db-path", args), "D:/mongo-data/rs0")),
                windowsPath(orDefault(raw("mongod-exe", args),
                        "C:/Program Files/MongoDB/Server/8.3/bin/mongod.exe")),
                blankToNull(raw("mongosh-exe", args)),
                blankToNull(raw("mongo-tools-bin", args)),
                compose.toString(),
                orDefault(raw("compose-service", args), "mongo"),
                intValue("wait-seconds", args, 60),
                bool("seed.enabled", args, true),
                bool("seed.books", args, false),
                bool("fail-fast", args, false),
                dir);
    }

    private static Mode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return Mode.AUTO;
        }
        try {
            return Mode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.AUTO;
        }
    }

    private static String raw(String key, String[] args) {
        String fullKey = PREFIX + key;

        String fromArgs = fromArgs(fullKey, args);
        if (fromArgs != null) {
            return fromArgs;
        }
        String fromSystem = System.getProperty(fullKey);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem.trim();
        }
        String fromEnv = System.getenv(fullKey.toUpperCase().replace('.', '_').replace('-', '_'));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromProps = CLASSPATH_PROPERTIES.getProperty(fullKey);
        if (fromProps != null && !fromProps.isBlank() && !fromProps.contains("${")) {
            return fromProps.trim();
        }
        return null;
    }

    /** Ho tro ca {@code --key=value} va {@code --key value}; {@code --key} (co) = true. */
    private static String fromArgs(String fullKey, String[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq >= 0) {
                if (body.substring(0, eq).equals(fullKey)) {
                    return body.substring(eq + 1);
                }
                continue;
            }
            if (body.equals(fullKey)) {
                if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                    return args[i + 1];
                }
                return "true";
            }
        }
        return null;
    }

    private static boolean bool(String key, String[] args, boolean defaultValue) {
        String value = raw(key, args);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized) || "yes".equalsIgnoreCase(normalized);
    }

    private static int intValue(String key, String[] args, int defaultValue) {
        String value = raw(key, args);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String orDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Windows API nhan ca '/' - chuan hoa ve '\' cho de doc trong log. */
    private static String windowsPath(String value) {
        return value == null ? null : value.replace('/', '\\');
    }

    private static Properties loadClasspathProperties() {
        Properties properties = new Properties();
        try (InputStream in = MongoDevBootstrapSettings.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Khong doc duoc thi dung gia tri mac dinh - khong lam app dung.
        }
        return properties;
    }

    /** Kiem tra file/exe co ton tai (an toan, khong nem exception). */
    static boolean isReadableFile(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(Path.of(path));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
