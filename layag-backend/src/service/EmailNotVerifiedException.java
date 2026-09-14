package service;

/**
 * Thrown when login credentials are correct but the account has not yet been
 * verified by the email code. The API layer catches this separately from a
 * wrong-password error so it can re-send the code and explain the next step.
 */
public class EmailNotVerifiedException extends RuntimeException {
    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Email not verified.");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}