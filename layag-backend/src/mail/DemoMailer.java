package mail;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simulated mailer: prints the "email" to the console and keeps a copy in an
 * in-memory inbox so the demo UI can fetch verification codes without a real
 * SMTP account. Enable a real implementation for production — mirrors the
 * MockPayGateway honesty statement for the capstone.
 */
public class DemoMailer implements Mailer {
    public static final class Message {
        private final String to;
        private final String subject;
        private final String body;

        Message(String to, String subject, String body) {
            this.to = to;
            this.subject = subject;
            this.body = body;
        }

        public String getTo() { return to; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
    }

    public static final CopyOnWriteArrayList<Message> inbox = new CopyOnWriteArrayList<>();

    public DemoMailer() {
    }

    @Override
    public void send(String to, String subject, String body) {
        inbox.add(new Message(to, subject, body));
        System.out.println("[MAIL-SIM] to=" + to + " subject=" + subject + " body=" + body);
    }

    public static void clear() {
        inbox.clear();
    }
}