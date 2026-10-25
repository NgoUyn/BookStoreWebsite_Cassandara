import org.mindrot.jbcrypt.BCrypt;
import java.util.Arrays;

public class GeneratePassword {
    public static void main(String[] args) {
        char[] input = System.console().readPassword(
            "Nhap mat khau moi: "
        );

        if (input == null || input.length < 12) {
            System.out.println("Mat khau phai co it nhat 12 ky tu.");
            return;
        }

        String hash = BCrypt.hashpw(
            new String(input),
            BCrypt.gensalt(12)
        );

        Arrays.fill(input, '\0');
        System.out.println("BCrypt hash: " + hash);
    }
}