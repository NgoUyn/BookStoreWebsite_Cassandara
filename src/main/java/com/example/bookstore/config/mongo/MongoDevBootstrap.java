package com.example.bookstore.config.mongo;

import com.example.bookstore.config.mongo.MongoDevBootstrapSettings.Mode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bootstrap DEV cho MongoDB: duoc goi tu
 * {@link com.example.bookstore.BookStoreApplication#main(String[])} TRUOC
 * {@code SpringApplication.run(...)} nen chi can bam Run 1 lan la chay het:
 *
 * <ol>
 *   <li>Cong 27018 da mo? -> kiem tra PRIMARY + seed (neu can) roi thoat nhanh.</li>
 *   <li>Chua mo -> start mongod.exe (mac dinh) hoac docker compose.</li>
 *   <li>Cho den khi instance o trang thai PRIMARY (poll 1s, toi da
 *       {@code app.mongo.autostart.wait-seconds}).</li>
 *   <li>DB/collection rong -> chay {@code db/mongo/01,02,03} + 11 (nha ban that).</li>
 * </ol>
 *
 * <p><b>An toan:</b> khong dung toi cong 27017 (service MongoDB cua Windows),
 * khong kill process, khong xoa du lieu, khong {@code docker compose down -v}.
 * Khi app tat thi mongod VAN CHAY (giong {@code tools\mongo-dev-start.bat}).</p>
 *
 * <p><b>Tat:</b> {@code --app.mongo.autostart.enabled=false} hoac bien moi truong
 * {@code APP_MONGO_AUTOSTART=false}. Neu bootstrap that bai, app van khoi dong
 * (tru khi dat {@code app.mongo.autostart.fail-fast=true}).</p>
 *
 * <p>Luu y: lop nay chi duoc goi tu {@code main()} nen KHONG anh huong test
 * ({@code @SpringBootTest}/{@code @DataMongoTest} khong goi {@code main()}).</p>
 */
@Slf4j
public final class MongoDevBootstrap {

    private static final String TAG = "[MongoDevBootstrap]";
    private static final int SOCKET_TIMEOUT_MS = 500;
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private MongoDevBootstrap() {
    }

    /** Diem vao duy nhat - goi tu {@code BookStoreApplication.main()}. */
    public static void prepare(String[] args) {
        MongoDevBootstrapSettings settings;
        try {
            settings = MongoDevBootstrapSettings.load(args);
        } catch (RuntimeException e) {
            log.warn("{} Khong doc duoc cau hinh ({}): {}", TAG, e.getClass().getSimpleName(), e.getMessage());
            return;
        }
        if (!settings.enabled()) {
            log.info("{} DA TAT (app.mongo.autostart.enabled=false) - bo qua buoc kiem tra MongoDB.", TAG);
            return;
        }

        long started = System.currentTimeMillis();
        try {
            if (isPortOpen(settings.host(), settings.port())) {
                log.info("{} MongoDB {}:{} da chay - kiem tra replica set + du lieu ...",
                        TAG, settings.host(), settings.port());
                if (!ensurePrimary(settings)) {
                    fail(settings, "Instance " + settings.host() + ":" + settings.port()
                            + " khong o trang thai PRIMARY");
                    return;
                }
                ensureSeeded(settings);
                log.info("{} OK ({} ms) - Spring context bat dau khoi dong.",
                        TAG, System.currentTimeMillis() - started);
                return;
            }

            log.info("{} MongoDB {}:{} CHUA chay -> dang khoi dong (mode={}) ...",
                    TAG, settings.host(), settings.port(), settings.mode());
            if (!startMongo(settings)) {
                fail(settings, "Khong khoi dong duoc MongoDB");
                return;
            }
            if (!waitForPrimary(settings)) {
                fail(settings, "MongoDB khong len PRIMARY sau " + settings.waitSeconds() + "s");
                return;
            }
            ensureSeeded(settings);
            log.info("{} SAN SANG ({} ms). Log mongod: {}",
                    TAG, System.currentTimeMillis() - started, settings.mongodLog());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} Bi ngat khi dang bootstrap MongoDB.", TAG);
        } catch (Exception e) {
            log.warn("{} Loi khi bootstrap MongoDB ({}): {}", TAG, e.getClass().getSimpleName(), e.getMessage());
            if (settings.failFast()) {
                throw new IllegalStateException("MongoDevBootstrap that bai: " + e.getMessage(), e);
            }
        }
    }

    private static void fail(MongoDevBootstrapSettings settings, String message) {
        log.warn("{} {} -> chay thu cong: tools\\mongo-dev-start.bat"
                + " (hoac xem docs\\mongodb\\DESIGN.md, log: {})", TAG, message, settings.mongodLog());
        if (settings.failFast()) {
            throw new IllegalStateException("MongoDevBootstrap that bai: " + message);
        }
    }

    // ------------------------------------------------------------------
    // Kiem tra cong / chay lenh ngoai
    // ------------------------------------------------------------------

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Ket qua 1 lenh ngoai (exit code + stdout/stderr gop chung). */
    private record CommandResult(int exitCode, String output) {
        boolean ok() {
            return exitCode == 0;
        }
    }

    private static CommandResult run(List<String> command, int timeoutSeconds)
            throws IOException, InterruptedException {
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
                // process ket thuc / stream dong - khong phai loi nghiem trong
            }
        }, "mongo-bootstrap-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        reader.join(2000);

        String text;
        synchronized (output) {
            text = output.toString().trim();
        }
        return new CommandResult(finished ? process.exitValue() : -1, text);
    }

    // ------------------------------------------------------------------
    // Do duong dan mongosh / mongoimport / mongod
    // ------------------------------------------------------------------

    private static String resolveMongosh(MongoDevBootstrapSettings settings) {
        if (MongoDevBootstrapSettings.isReadableFile(settings.mongoshExe())) {
            return settings.mongoshExe();
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

    /** Tim exe trong bin cua MongoDB Database Tools (uu tien version moi nhat) hoac PATH. */
    private static String resolveMongoTool(MongoDevBootstrapSettings settings, String tool) {
        if (settings.mongoToolsBin() != null && !settings.mongoToolsBin().isBlank()) {
            Path candidate = Path.of(settings.mongoToolsBin(), tool + ".exe");
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        Path toolsRoot = Path.of("C:\\Program Files\\MongoDB\\Tools");
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
                // bo qua - thu tiep PATH
            }
        }
        return onPath(tool);
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
                // entry PATH khong hop le - bo qua
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // mongosh: --eval / --file (dung --host/--port nen khong can quote URI)
    // ------------------------------------------------------------------

    private static CommandResult mongoshEval(MongoDevBootstrapSettings settings, String script)
            throws IOException, InterruptedException {
        String mongosh = resolveMongosh(settings);
        if (mongosh == null) {
            throw new IOException("Khong tim thay mongosh.exe (dat app.mongo.autostart.mongosh-exe"
                    + " hoac cai: winget install --id MongoDB.Shell -e)");
        }
        return run(List.of(mongosh, "--host", settings.host(), "--port", String.valueOf(settings.port()),
                "--quiet", "--eval", script), 60);
    }

    private static CommandResult mongoshFile(MongoDevBootstrapSettings settings, Path file)
            throws IOException, InterruptedException {
        String mongosh = resolveMongosh(settings);
        if (mongosh == null) {
            throw new IOException("Khong tim thay mongosh.exe (dat app.mongo.autostart.mongosh-exe)");
        }
        return run(List.of(mongosh, "--host", settings.host(), "--port", String.valueOf(settings.port()),
                "--file", file.toString()), 900);
    }

    // ------------------------------------------------------------------
    // Replica set: init (idempotent) + cho PRIMARY
    // ------------------------------------------------------------------

    private static boolean ensurePrimary(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        if (isWritablePrimary(settings)) {
            return true;
        }
        Path initScript = settings.dbScript("00_init_replica_set.js");
        if (!Files.isRegularFile(initScript)) {
            log.warn("{} Khong thay {} -> khong the init replica set.", TAG, initScript);
            return false;
        }
        log.info("{} Chua phai PRIMARY -> chay 00_init_replica_set.js ...", TAG);
        mongoshFile(settings, initScript);
        return waitForPrimary(settings);
    }

    private static boolean waitForPrimary(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        int waited = 0;
        while (waited < settings.waitSeconds()) {
            if (isPortOpen(settings.host(), settings.port()) && isWritablePrimary(settings)) {
                log.info("{} MongoDB da o trang thai PRIMARY (sau ~{}s).", TAG, waited);
                return true;
            }
            Thread.sleep(1000);
            waited++;
        }
        return false;
    }

    /** Doc dong cuoi cua mongosh --eval (mongosh co the in warning ra stderr). */
    private static boolean isWritablePrimary(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        CommandResult result = mongoshEval(settings, "db.hello().isWritablePrimary");
        return result.ok() && "true".equalsIgnoreCase(lastLine(result.output()));
    }

    // ------------------------------------------------------------------
    // Seed du lieu khi DB/collection con rong (idempotent)
    // ------------------------------------------------------------------

    private static long countDocuments(MongoDevBootstrapSettings settings, String collection, String filter)
            throws IOException, InterruptedException {
        String script = "db.getSiblingDB('" + settings.database() + "').getCollection('" + collection
                + "').countDocuments(" + filter + ")";
        CommandResult result = mongoshEval(settings, script);
        if (!result.ok()) {
            log.warn("{} Khong dem duoc {}: {}", TAG, collection, tail(result.output(), 200));
            return -1;
        }
        String value = firstNumber(lastLine(result.output()));
        return value == null ? -1 : Long.parseLong(value);
    }

    private static boolean collectionsEmpty(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        String script = "db.getSiblingDB('" + settings.database()
                + "').getCollectionNames().filter(n => !n.startsWith('system.')).length";
        CommandResult result = mongoshEval(settings, script);
        if (!result.ok()) {
            return false;
        }
        String value = firstNumber(lastLine(result.output()));
        return value != null && Long.parseLong(value) == 0;
    }

    private static void runDbScript(MongoDevBootstrapSettings settings, String fileName)
            throws IOException, InterruptedException {
        Path script = settings.dbScript(fileName);
        if (!Files.isRegularFile(script)) {
            log.warn("{} Khong thay script {} - bo qua.", TAG, script);
            return;
        }
        CommandResult result = mongoshFile(settings, script);
        log.info("{} [{}] exit={} {}", TAG, fileName, result.exitCode(), tail(result.output(), 300));
        if (!result.ok()) {
            log.warn("{} [{}] THAT BAI - xem log: {} | {}", TAG, fileName, settings.mongodLog(),
                    tail(result.output(), 600));
        }
    }

    private static void ensureSeeded(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        if (!settings.seedEnabled()) {
            log.info("{} Bo qua buoc seed (app.mongo.autostart.seed.enabled=false).", TAG);
            return;
        }

        if (collectionsEmpty(settings)) {
            log.info("{} Database `{}` dang RONG -> chay 01/02/03"
                            + " (collection + validator + index + danh muc) ...",
                    TAG, settings.database());
            runDbScript(settings, "01_create_collections_validators.js");
            runDbScript(settings, "02_indexes.js");
            runDbScript(settings, "03_seed_reference.js");
        } else {
            log.info("{} Database `{}` da co du lieu - khong tao lai schema.",
                    TAG, settings.database());
        }

        long sellers = countDocuments(settings, "users", "{role:'SELLER'}");
        if (sellers == 0) {
            Path sellersData = settings.dbScript("11_sellers_data.js");
            if (Files.isRegularFile(sellersData)) {
                log.info("{} Chua co nha ban (SELLER) -> chay 11_import_sellers.js ...", TAG);
                runDbScript(settings, "11_import_sellers.js");
            } else {
                log.warn("{} Chua co {} -> chay: tools\\build-sellers-seed.bat", TAG, sellersData);
            }
        } else if (sellers > 0) {
            log.info("{} Da co {} nha ban (SELLER).", TAG, sellers);
        }

        long books = countDocuments(settings, "books", "{}");
        if (books > 0) {
            log.info("{} `books` da co {} sach - khong import lai.", TAG, books);
            return;
        }
        if (books < 0) {
            return;
        }
        if (!settings.seedBooks()) {
            log.warn("{} `books` dang RONG. Chay 1 lan (mat vai phut): tools\\mongo-import-books.bat"
                            + " - hoac dat app.mongo.autostart.seed.books=true de tu dong import.",
                    TAG);
            return;
        }
        log.info("{} `books` dang RONG -> mongoimport Books.csv + 12_import_books_csv.js (vai phut) ...", TAG);
        importBooksCsv(settings);
        runDbScript(settings, "12_import_books_csv.js");
        log.info("{} `books` sau import = {} sach.", TAG, countDocuments(settings, "books", "{}"));
    }

    /** Giong tools\mongo-import-books.bat (buoc 1): CSV -> books_raw. */
    private static void importBooksCsv(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        Path csv = settings.projectDir().resolve("db").resolve("seed").resolve("Books.csv");
        if (!Files.isRegularFile(csv)) {
            log.warn("{} Khong thay CSV {} -> bo qua buoc import sach.", TAG, csv);
            return;
        }
        String mongoimport = resolveMongoTool(settings, "mongoimport");
        if (mongoimport == null) {
            log.warn("{} Khong thay mongoimport.exe -> cai: winget install --id MongoDB.DatabaseTools -e", TAG);
            return;
        }
        CommandResult result = run(List.of(mongoimport,
                "--uri=" + settings.uri(),
                "--collection=books_raw",
                "--type=csv",
                "--columnsHaveTypes",
                "--fields=ISBN.string(),Book-Title.string(),Book-Author.string(),Year-Of-Publication.string(),"
                        + "Publisher.string(),Image-URL-S.string(),Image-URL-M.string(),Image-URL-L.string()",
                "--file=" + csv,
                "--numInsertionWorkers=4"), 1800);
        log.info("{} mongoimport -> books_raw: exit={} {}", TAG, result.exitCode(), tail(result.output(), 300));
        if (!result.ok()) {
            log.warn("{} mongoimport THAT BAI - chay lai bang tools\\mongo-import-books.bat de xem chi tiet.", TAG);
        }
    }

    // ------------------------------------------------------------------
    // Khoi dong MongoDB (mongod.exe hoac docker compose)
    // ------------------------------------------------------------------

    private static boolean startMongo(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        boolean hasNativeMongod = MongoDevBootstrapSettings.isReadableFile(settings.mongodExe());
        Mode mode = settings.mode();
        if (mode == Mode.AUTO) {
            mode = hasNativeMongod ? Mode.NATIVE : Mode.DOCKER;
        }
        if (mode == Mode.NATIVE) {
            if (!hasNativeMongod) {
                log.warn("{} Khong thay mongod.exe tai `{}` -> sua app.mongo.autostart.mongod-exe"
                        + " (hoac dat mode=docker neu dung Docker Desktop).", TAG, settings.mongodExe());
                return false;
            }
            return startNativeMongod(settings);
        }
        return startDockerMongo(settings);
    }

    private static boolean startNativeMongod(MongoDevBootstrapSettings settings) throws IOException {
        Path dbPath = Path.of(settings.dbPath());
        Files.createDirectories(dbPath);
        Path logPath = settings.mongodLog();

        ProcessBuilder builder = new ProcessBuilder(List.of(settings.mongodExe(),
                "--dbpath", dbPath.toString(),
                "--port", String.valueOf(settings.port()),
                "--replSet", settings.replicaSet(),
                "--bind_ip", settings.host(),
                "--logpath", logPath.toString(),
                "--logappend"));
        // mongod ghi log ra --logpath; bo stdout de khong giu pipe cua JVM.
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!process.isAlive()) {
            log.warn("{} mongod thoat ngay sau khi start (exit={}) - xem {}", TAG, process.exitValue(), logPath);
            return false;
        }
        log.info("{} Da start mongod (pid={}) | dbpath={} | port={} | replSet={} | log={}",
                TAG, process.pid(), dbPath, settings.port(), settings.replicaSet(), logPath);
        return true;
    }

    private static boolean startDockerMongo(MongoDevBootstrapSettings settings)
            throws IOException, InterruptedException {
        Path compose = Path.of(settings.composeFile());
        if (!Files.isRegularFile(compose)) {
            log.warn("{} Khong thay {} -> khong the dung docker compose.", TAG, compose);
            return false;
        }
        CommandResult info = run(List.of("docker", "info", "--format", "{{.ServerVersion}}"), 60);
        if (!info.ok()) {
            log.warn("{} Docker Desktop chua BAT (docker info that bai: {})", TAG, tail(info.output(), 200));
            return false;
        }
        log.info("{} Dang chay: docker compose -f {} up -d {} ...", TAG, compose, settings.composeService());
        CommandResult up = run(List.of("docker", "compose", "-f", compose.toString(),
                "up", "-d", settings.composeService()), 300);
        log.info("{} docker compose up -> exit={} {}", TAG, up.exitCode(), tail(up.output(), 400));
        if (!up.ok()) {
            log.warn("{} docker compose THAT BAI. Luu y: docker-compose.mongo.yml dang map cong 27017"
                    + " (da bi service MongoDB cua Windows chiem) - doi thanh \"{}:27017\".",
                    TAG, settings.port());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tien ich xu ly output
    // ------------------------------------------------------------------

    private static String lastLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.strip().split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].trim();
            }
        }
        return "";
    }

    private static String firstNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /** Rut gon output 1 dong de log gon (giu phan cuoi - noi co loi). */
    private static String tail(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= maxLength ? flat : "..." + flat.substring(flat.length() - maxLength);
    }
}
