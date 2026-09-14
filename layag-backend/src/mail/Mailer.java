package mail;

/**
 * How LAYAG sends transactional email. Real deployments implement this with a
 * mail library (Jakarta Mail) + SMTP credentials; the capstone uses
 * DemoMailer, which simulates the send exactly like MockPayGateway simulates
 * the payment gateway.
 */
public interface Mailer {
    void send(String to, String subject, String body);

    /**
     * True for simulated transports (DemoMailer). Real transports (ResendMailer)
     * return false so the demo mailbox endpoint is disabled.
     */
    default boolean isSimulated() {
        return true;
    }
}