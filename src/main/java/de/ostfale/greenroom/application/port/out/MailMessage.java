package de.ostfale.greenroom.application.port.out;

import static de.ostfale.greenroom.domain.Texts.required;

/**
 * One mail, ready to go out. Nothing in it is composed here: the German comes from the
 * page, where it was shown and could be edited before it was sent. What reaches this
 * record is what the sender actually wrote.
 *
 * <p>Sender and copy are not fields — they are the same for every mail this application
 * sends and belong in the configuration, not in every call.
 */
public record MailMessage(String to, String subject, String body) {

    public MailMessage {
        to = required(to, "MailMessage :: a mail needs somebody to go to");
        subject = required(subject, "MailMessage :: a mail needs a subject");
        body = required(body, "MailMessage :: an empty mail is not worth sending");
    }
}
