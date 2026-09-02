package de.ostfale.greenroom.application.port.out;

/**
 * The way out to the mail server. One method, because there is one kind of mail: plain
 * text, one recipient, no attachments.
 */
public interface SendMail {

    /**
     * Hands the mail to the server.
     *
     * @throws MailNotSent if it did not go out — the caller must not record an inquiry
     *                     that never left the house
     */
    void send(MailMessage message);

    /** The mail did not go out, and the reason is worth showing on the page. */
    class MailNotSent extends RuntimeException {

        public MailNotSent(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
