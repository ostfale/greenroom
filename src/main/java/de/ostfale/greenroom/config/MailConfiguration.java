package de.ostfale.greenroom.config;

import de.ostfale.greenroom.adapter.out.mail.LoggingMailer;
import de.ostfale.greenroom.adapter.out.mail.SmtpMailer;
import de.ostfale.greenroom.application.port.out.SendMail;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Which way out the mail takes. One bean and one decision, rather than a condition on each
 * of the two classes: {@code @ConditionalOnMissingBean} outside auto-configuration depends
 * on the order beans happen to be found in, and this must not be a matter of luck.
 *
 * <p>No host means no mail. That is the state in development and in the tests, and it has
 * to be the harmless one — a wrong default here writes to a real speaker.
 */
@Configuration(proxyBeanMethods = false)
public class MailConfiguration {

    @Bean
    public SendMail mailer(ObjectProvider<JavaMailSender> sender,
                           @Value("${spring.mail.host:}") String host,
                           @Value("${greenroom.mail.from:}") String from) {
        if (host.isBlank() || from.isBlank()) {
            return new LoggingMailer();
        }
        return new SmtpMailer(sender.getObject(), from);
    }
}
