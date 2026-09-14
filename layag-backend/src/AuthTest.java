import model.User;
import repository.UserRepository;
import repository.UserRepositoryJdbc;
import service.AuthService;

public class AuthTest {
    public static void main(String[] args) {
        UserRepository repo = new UserRepositoryJdbc();
        AuthService auth = new AuthService(repo);

        System.out.println("=== REGISTER ROLES ===");
        auth.register("Andre Sender", "andre@layag.ph", "pass123", "SENDER");
        auth.register("Ana Courier", "ana@layag.ph", "rider123", "COURIER");
        auth.register("Admin", "admin@layag.ph", "admin123", "ADMIN");

        System.out.println("=== LOGIN: VALID CREDENTIALS ===");
        User u = auth.login("ana@layag.ph", "rider123");
        System.out.println("Logged in: " + u.getName() + " | role: " + u.getRole());

        System.out.println();
        System.out.println("=== LOGIN: WRONG PASSWORD ===");
        try {
            auth.login("ana@layag.ph", "wrongpass");
            System.out.println("FAIL: should have thrown");
        } catch (IllegalArgumentException e) {
            System.out.println("Blocked (correct): " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== LOGIN: UNKNOWN EMAIL ===");
        try {
            auth.login("ghost@layag.ph", "x");
            System.out.println("FAIL: should have thrown");
        } catch (IllegalArgumentException e) {
            System.out.println("Blocked (correct): " + e.getMessage());
        }
    }
}