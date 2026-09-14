package config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Central configuration lookup. Precedence:
 *   1. config.properties in the working directory
 *   2. environment variables (LAYAG_*)
 *   3. built-in development defaults
 *
 * In production mode (production=true in config.properties, or the
 * LAYAG_PRODUCTION env var = true), any key that has no explicit value is
 * rejected with an error instead of silently using a default. This prevents
 * accidentally deploying a server with a well-known default password.
 */
public final class Config {
    private Config() {
    }

    private static final Path FILE = Path.of("config.properties");
    private static final Properties PROPS = new Properties();

    static {
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                PROPS.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Cannot read " + FILE, e);
            }
        }
    }

    /** True when the server must refuse development defaults. */
    public static boolean production() {
        String p = fromPropsEnv("production", "LAYAG_PRODUCTION");
        return "true".equalsIgnoreCase(p) || "1".equals(p);
    }

    /**
     * Returns the configured value for a key, throwing in production when the
     * value is missing.
     */
    public static String get(String key, String envName, String def) {
        String v = fromPropsEnv(key, envName);
        if (v == null || v.isBlank()) {
            if (production()) {
                throw new IllegalStateException(
                        "Config key '" + key + "' is missing. Set it in config.properties "
                        + "or the " + envName + " environment variable before running in production.");
            }
            return def;
        }
        return v.trim();
    }

    /** Same as get() for integer values. */
    public static int getInt(String key, String envName, int def) {
        return Integer.parseInt(get(key, envName, String.valueOf(def)));
    }

    private static String fromPropsEnv(String key, String envName) {
        String v = PROPS.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(envName);
        }
        return (v == null) ? null : v.trim();
    }
}