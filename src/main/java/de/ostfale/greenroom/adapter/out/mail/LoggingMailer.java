package de.ostfale.greenroom.adapter.out.mail;

import de.ostfale.greenroom.application.port.out.MailMessage;
import de.ostfale.greenroom.application.port.out.SendMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What runs when no mail host is configured: the mail is written to the log and goes
 * nowhere. Development and the tests must not reach a real speaker, and a mailer that is
 * simply absent would take the whole page down with it.
 */
public class LoggingMailer implements SendMail {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void send(MailMessage message) {
        log.info("LoggingMailer :: no mail host configured, so this went nowhere:\nTo: {}\n{}\n\n{}",
                message.to(), message.subject(), message.body());
    }
}
