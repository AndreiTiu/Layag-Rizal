package service;

import mail.Mailer;
import model.User;
import repository.UserRepository;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email verification: every new account gets a 6-digit code that "expires"
 * (10 minutes) and is spent by confirming it. Until the code is confirmed the
 * account cannot log in, which stops sign-ups with fake/unowned emails.
 *
 * Codes live in memory (like the session store); is_verified is persisted in
 * the users table. The mailer is simulated (DemoMailer) — production swaps in
 * a real SMTP implementation.
 */
public class EmailVerificationService {
    private static final long CODE_TTL_MS = 10 * 60 * 1000;
    private static final int MAX_ATTEMPTS = 5;

    private static final class Entry {
        final int userId;
        final String code;
        final long expiresAt;
        int attempts;

        Entry(int userId, String code, long expiresAt) {
            this.userId = userId;
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private final UserRepository userRepository;
    private final Mailer mailer;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();

    public EmailVerificationService(UserRepository userRepository, Mailer mailer) {
        this.userRepository = userRepository;
        this.mailer = mailer;
    }

    public void start(int userId, String email) {
        String code = sixDigits();
        codes.put(email, new Entry(userId, code, System.currentTimeMillis() + CODE_TTL_MS));
        mailer.send(email, "LAYAG — verify your email",
            "Your LAYAG verification code is " + code + ".\nIt expires in 10 minutes.");
    }

    /** Returns true when the code was correct and the account is now verified. */
    public boolean verify(String email, String code) {
        String norm = (email == null) ? "" : email.trim().toLowerCase();
        Entry e = codes.get(norm);
        if (e == null) {
            return false;
        }
        if (System.currentTimeMillis() > e.expiresAt || e.attempts >= MAX_ATTEMPTS) {
            codes.remove(norm);
            return false;
        }
        String given = (code == null) ? null : code.trim();
        if (!java.util.Objects.equals(e.code, given)) {
            e.attempts++;
            return false;
        }
        userRepository.setVerified(e.userId);
        codes.remove(norm);
        return true;
    }

    /**
     * Re-sends a fresh code if the account exists AND is not yet verified.
     * Silent about accounts that don't exist (avoids email enumeration).
     *
     * @return true when a code was re-sent
     */
    public boolean resend(String email) {
        String norm = (email == null) ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(norm);
        if (user == null || user.isVerified()) {
            return false;
        }
        start(user.getUserId(), norm);
        return true;
    }

    private String sixDigits() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}