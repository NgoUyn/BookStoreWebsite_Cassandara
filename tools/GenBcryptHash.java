import org.mindrot.jbcrypt.BCrypt;

/**
 * Cong cu sinh hash bcrypt THAT cho tai khoan demo (dung khi can tao tai khoan
 * trong MongoDB bang script thay vi qua API dang ky).
 *
 * <p>Chay (khong can Maven, chi can JDK 17+ va jar jbcrypt):</p>
 * <pre>
 * java -cp "%USERPROFILE%\.m2\repository\org\mindrot\jbcrypt\0.4\jbcrypt-0.4.jar" ^
 *      tools\GenBcryptHash.java "matkhau1" "matkhau2"
 * </pre>
 *
 * <p>Cost mac dinh 12 - dung bang {@code AuthService} nen
 * {@code BCrypt.checkpw()} cua ung dung se khop.</p>
 */
public class GenBcryptHash {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Cach dung: java -cp <jbcrypt.jar> GenBcryptHash.java <matkhau> [matkhau2 ...]");
            return;
        }
        BCrypt.checkpw("x", BCrypt.hashpw("x", BCrypt.gensalt(12))); // warm-up, kiem tra jar
        for (String raw : args) {
            String hash = BCrypt.hashpw(raw, BCrypt.gensalt(12));
            System.out.println(raw + " => " + hash);
        }
    }
}
