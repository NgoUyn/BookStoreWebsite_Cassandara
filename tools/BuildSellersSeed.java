import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BuildSellersSeed - doc danh sach ten nha ban THAT (db/seed/sellers_real.txt)
 * va sinh file du lieu seed cho MongoDB, kem HASH BCRYPT THAT (cost 12).
 *
 * <p>Quy uoc sinh tu ten:</p>
 * <pre>
 *   email    : &lt;ten-bo-dau-viet-lien&gt;@bookom.vn   vd nhaxuatbantre@bookom.vn
 *   slug shop: nha-xuat-ban-tre
 *   mat khau : &lt;Tenbodauvietlien&gt;123@            vd Nhaxuatbantre123@
 * </pre>
 *
 * <p>Chay: tools\build-sellers-seed.bat</p>
 */
public class BuildSellersSeed {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern SPACE_RUN = Pattern.compile("\\s+");

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args.length > 0 ? args[0] : "db/seed/sellers_real.txt");
        Path out = Path.of(args.length > 1 ? args[1] : "db/mongo/11_sellers_data.js");
        if (!Files.exists(in)) {
            System.err.println("Khong tim thay file danh sach: " + in.toAbsolutePath());
            System.exit(1);
        }

        List<String> names = readNames(in);
        List<String> rows = new ArrayList<>();
        Set<String> usedEmail = new HashSet<>();
        Set<String> usedSlug = new HashSet<>();

        for (String name : names) {
            String compact = compact(name);                 // "Nhaxuatbantre"
            String emailLocal = compact.toLowerCase();      // "nhaxuatbantre"
            String password = compact + "123@";             // "Nhaxuatbantre123@"
            String slug = slugify(name);                    // "nha-xuat-ban-tre"

            String email = emailLocal + "@bookom.vn";
            int n = 2;
            while (usedEmail.contains(email)) {
                email = emailLocal + n++ + "@bookom.vn";
            }
            usedEmail.add(email);

            int m = 2;
            String uniqueSlug = slug;
            while (usedSlug.contains(uniqueSlug)) {
                uniqueSlug = slug + "-" + m++;
            }
            usedSlug.add(uniqueSlug);

            String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            rows.add(json(name, uniqueSlug, email, password, hash));
        }

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("// ============================================================================\n");
            w.write("// 11_sellers_data.js - DU LIEU SEED NHA BAN (AUTO-GENERATED)\n");
            w.write("//   Sinh boi: tools\\BuildSellersSeed.java  <--  db/seed/sellers_real.txt\n");
            w.write("//   KHONG sua file nay bang tay. Muon sua ten: sua sellers_real.txt roi chay\n");
            w.write("//   tools\\build-sellers-seed.bat\n");
            w.write("//   passwordHash la bcrypt THAT (cost 12) - dang nhap duoc bang email + mat khau.\n");
            w.write("// ============================================================================\n\n");
            w.write("const SELLERS = [\n");
            for (int i = 0; i < rows.size(); i++) {
                w.write("  " + rows.get(i) + (i < rows.size() - 1 ? "," : "") + "\n");
            }
            w.write("];\n");
        }

        System.out.println("Da sinh " + rows.size() + " nha ban -> " + out.toAbsolutePath());
        System.out.println("Vi du quy uoc (ten | email | mat khau):");
        for (int i = 0; i < Math.min(5, names.size()); i++) {
            String compactName = compact(names.get(i));
            System.out.println("   " + names.get(i) + " | " + compactName.toLowerCase()
                    + "@bookom.vn | " + compactName + "123@");
        }
    }

    private static List<String> readNames(Path in) throws Exception {
        List<String> names = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                names.add(s);
            }
        }
        return names;
    }

    /** Bo dau tieng Viet + bo khoang trang; chu dau HOA, con lai thuong. */
    private static String compact(String raw) {
        String noAccent = stripAccents(raw);
        String letters = NON_ALNUM.matcher(noAccent).replaceAll("");
        if (letters.isEmpty()) return "Seller";
        return Character.toUpperCase(letters.charAt(0)) + letters.substring(1).toLowerCase();
    }

    /** Slug URL: bo dau, thay khoang trang bang '-'. */
    private static String slugify(String raw) {
        String noAccent = stripAccents(raw).replaceAll("['\"().,/]", "");
        String withHyphen = SPACE_RUN.matcher(noAccent.trim()).replaceAll("-");
        String slug = withHyphen.toLowerCase().replaceAll("[^a-z0-9-]", "").replaceAll("-{2,}", "-");
        return slug.isEmpty() ? "shop" : slug;
    }

    /** NFD + bo dau ket hop; 'd'/'D' xu ly rieng vi NFD khong tach duoc. */
    private static String stripAccents(String raw) {
        String s = raw.replace('đ', 'd').replace('Đ', 'D');
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String json(String name, String slug, String email, String password, String hash) {
        return "{ name: \"" + esc(name) + "\", slug: \"" + esc(slug) + "\", email: \"" + esc(email)
                + "\", password: \"" + esc(password) + "\", hash: \"" + esc(hash) + "\" }";
    }
}
