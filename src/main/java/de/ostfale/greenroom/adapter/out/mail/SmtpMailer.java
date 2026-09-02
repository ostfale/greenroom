package de.ostfale.greenroom.adapter.out.mail;

import de.ostfale.greenroom.application.port.out.MailMessage;
import de.ostfale.greenroom.application.port.out.SendMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Hands the mail to the provider's server. Plain text and one recipient — an inquiry is a
 * letter, not a newsletter.
 *
 * <p>Every mail carries a blind copy to the sending address. The provider keeps sent mail
 * in the folder of whoever sent it through their own client, and this is not that client:
 * without the copy there would be no trace of the mail in the mailbox at all.
 *
 * <p>Only built when a host is configured — see {@code MailConfiguration}. Without one the
 * application runs with {@link LoggingMailer} instead, so development never reaches a real
 * speaker.
 */
public class SmtpMailer implements SendMail {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setReplyTo(from);
        mail.setBcc(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            sender.send(mail);
            log.info("SmtpMailer :: mail sent to {}", message.to());
        } catch (MailException e) {
            log.warn("SmtpMailer :: mail to {} did not go out", message.to(), e);
            throw new MailNotSent("SmtpMailer :: the mail server refused the message", e);
        }
    }
}
