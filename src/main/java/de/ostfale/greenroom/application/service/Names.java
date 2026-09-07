package de.ostfale.greenroom.application.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.Locale;

/**
 * The order a list of names is read in. Every list of places, speakers and tags goes
 * through here on its way to a page.
 *
 * <p>The database sorts by bytes. That puts every capital letter before every small one,
 * so {@code adesso} landed behind {@code Tchibo} — and it puts every umlaut behind
 * {@code z}, which would send {@code Körber} to the end of the list. A German collator
 * sorts the way a reader looks things up: the case decides nothing until two names are
 * otherwise the same, and {@code ö} stands with {@code o}.
 *
 * <p>Here rather than in the queries, for two reasons. The rule is one rule and would
 * otherwise stand in four places; and the collation a database was created with is not
 * something this application gets to choose — on the Pi it is whatever the image did. The
 * ports keep their {@code order by}: it makes what a port returns deterministic, which is
 * what their tests stand on. This decides what somebody reads.
 *
 * <p>A few hundred rows, one user, one machine: sorting them in memory costs nothing worth
 * measuring.
 */
final class Names {

    private Names() {
    }

    /** The list again, in the order a German reader expects it. */
    static <T> List<T> sorted(List<T> items, Function<T, String> name) {
        return items.stream()
                .sorted(Comparator.comparing(name, Collator.getInstance(Locale.GERMAN)))
                .toList();
    }
}
