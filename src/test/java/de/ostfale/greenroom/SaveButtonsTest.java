package de.ostfale.greenroom;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A save button that is lit from the moment the page loads says nothing about whether
 * anything was typed, and after saving it looks exactly as it did before. So every form
 * that can be sent comes out of the template marked {@code guarded} and with its button
 * switched off; the first change to a field switches it on.
 *
 * <p>Read off the templates rather than off a rendered page, because the answer has to
 * cover every form there is — a new one that forgets the mark is the case this catches.
 */
class SaveButtonsTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void everyFormThatSavesStartsWithItsButtonOff() throws IOException {
        for (Path page : templates()) {
            Document template = Jsoup.parse(page.toFile(), "UTF-8");
            for (Element form : template.select("form:has(button[type=submit])")) {
                assertThat(form.hasClass("guarded"))
                        .as("%s :: a form that saves has to be guarded", page.getFileName())
                        .isTrue();
                assertThat(form.select("button[type=submit]:not([disabled])"))
                        .as("%s :: the save has to start switched off", page.getFileName())
                        .isEmpty();
            }
        }
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<Path> pages = files.filter(file -> file.toString().endsWith(".html")).toList();
            assertThat(pages).as("no templates found under " + TEMPLATES).isNotEmpty();
            return pages;
        }
    }
}
