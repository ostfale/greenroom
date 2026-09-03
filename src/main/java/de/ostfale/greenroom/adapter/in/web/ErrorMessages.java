package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * What the page says when something was refused. The records name a {@link
 * de.ostfale.greenroom.domain.Rule}, this looks the German sentence up — the one place
 * where the two meet, so a controller has nothing left to decide about it.
 *
 * <p>Not a {@code @ControllerAdvice}: the refusals are caught inside the handler because
 * htmx expects the tile back with the stored state and the reason in it, and an exception
 * handler cannot build that model.
 */
@Component
class ErrorMessages {

    private final MessageSource messages;

    ErrorMessages(MessageSource messages) {
        this.messages = messages;
    }

    /** The sentence for this refusal, in German. */
    String german(Exception e) {
        return switch (e) {
            case RuleViolated violation -> text("rule." + violation.rule(), violation.args());
            default -> text("error.generic");
        };
    }

    /**
     * Without arguments the text is taken as it stands; with them it goes through
     * {@code MessageFormat}, which is why an apostrophe in such a text has to be doubled.
     */
    String text(String key, Object... args) {
        return messages.getMessage(key, args.length == 0 ? null : args,
                LocaleContextHolder.getLocale());
    }
}
