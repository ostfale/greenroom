package de.ostfale.greenroom;

import de.ostfale.greenroom.application.port.out.MailMessage;
import de.ostfale.greenroom.application.port.out.SendMail;

import java.util.ArrayList;
import java.util.List;

/**
 * A mailer that keeps what it was given instead of sending it, and refuses when a test
 * wants to see what happens then. A fake rather than a mock: the tests read what came out,
 * not which methods were called.
 */
public class FakeMailer implements SendMail {

    private final List<MailMessage> sent = new ArrayList<>();
    private boolean refusing;

    @Override
    public void send(MailMessage message) {
        if (refusing) {
            throw new MailNotSent("FakeMailer :: the mail server refused the message", null);
        }
        sent.add(message);
    }

    /** What went out, oldest first. */
    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public MailMessage onlyOne() {
        if (sent.size() != 1) {
            throw new IllegalStateException("FakeMailer :: " + sent.size() + " mails went out");
        }
        return sent.getFirst();
    }

    /** From now on the server says no. */
    public void refuse() {
        refusing = true;
    }

    public void forgetEverything() {
        sent.clear();
        refusing = false;
    }
}
