package security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HmacSHA256.
 *
 * WHY: a password hash must be slow to brute-force. SHA-256 is designed to be
 * FAST, which means an attacker who steals the hash table can try billions of
 * passwords per second. PBKDF2 is a key-derivation function built for this:
 * it repeats the hash hundreds of thousands of times, so every guess is
 * expensive. Each password also gets its own random salt, so equal passwords
 * produce different hashes and rainbow tables are useless.
 *
 * Stored format:  pbkdf2:<iterations>:<salt-base64>:<hash-base64>
 * Hashes created before this change used plain SHA-256 (64 hex chars). We can
 * detect that format and upgrade the account on its next successful login.
 */
public final class PasswordHasher {
    private PasswordHasher() {
    }

    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGO = "PBKDF2WithHmacSHA256";

    public static String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] dk = derive(raw, salt, ITERATIONS);
        return "pbkdf2:" + ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(dk);
    }

    /** Returns true if the password matches the stored hash. */
    public static boolean verify(String raw, String stored) {
        if (isLegacyHex(stored)) {
            return legacySha256(raw).equalsIgnoreCase(stored);
        }
        try {
            String[] parts = stored.split(":");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            // constant-time compare: timing must not reveal how much of the
            // hash matched, or an attacker can guess byte-by-byte.
            return MessageDigest.isEqual(derive(raw, salt, iterations), expected);
        } catch (Exception e) {
            return false;
        }
    }

    /** True if the stored value came from the old fast SHA-256 scheme. */
    public static boolean isLegacyHex(String stored) {
        if (stored == null || stored.length() != 64) {
            return false;
        }
        for (int i = 0; i < stored.length(); i++) {
            char c = stored.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return false;
            }
        }
        return true;
    }

    /** After a successful legacy login, re-hash into the secure format. */
    public static String upgrade(String raw, String stored) {
        if (isLegacyHex(stored) && verify(raw, stored)) {
            return hash(raw);
        }
        return null;
    }

    private static byte[] derive(String raw, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGO);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 not available", e);
        }
    }

    private static String legacySha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digested = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digested) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}