import org.mindrot.jbcrypt.BCrypt; public class Test{ public static void main(String[] a){ System.out.println(BCrypt.hashpw("admin123", BCrypt.gensalt(10))); }} 
