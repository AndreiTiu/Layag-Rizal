package mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Real email transport via the Resend REST API (free tier: 100 emails/day).
 * Uses only the JDK's built-in HTTP client — no extra library, mirroring how
 * the rest of the project talks to MySQL and would talk to a payment gateway.
 *
 * Resend replies over HTTPS with a JSON body. A failure (bad key, unverified
 * from-address, no internet) is surfaced as an exception so the caller knows
 * no code was actually delivered instead of pretending.
 */
public class ResendMailer implements Mailer {
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String apiKey;
    private final String from;

    public ResendMailer(String apiKey, String from) {
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        String json = "{\"from\":\"" + js(from) + "\",\"to\":\"" + js(to)
                + "\",\"subject\":\"" + js(subject) + "\",\"text\":\"" + js(body) + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IllegalStateException("Email API rejected the send (HTTP "
                        + res.statusCode() + "): " + trimBody(res.body()));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Email API unreachable: " + e.getMessage(), e);
        }
    }

    // A real transport has no demo mailbox to scrape.
    @Override
    public boolean isSimulated() {
        return false;
    }

    /** Minimal JSON string escaping for our message fields. */
    private static String js(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String trimBody(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) : s);
    }
}