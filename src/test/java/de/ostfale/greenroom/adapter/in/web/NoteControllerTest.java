package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageNotes;
import de.ostfale.greenroom.domain.notes.Note;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole slice: browser request, controller, use case, real Postgres — and back as
 * rendered HTML.
 */
@WebTest
class NoteControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageNotes notes;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void anEmptyBox() {
        database.empty();
    }

    @Test
    void theFormWritesASlipAndStampsIt() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        mvc.perform(post("/note")
                        .param("title", "Testcontainers-Abend?")
                        .param("text", "Wen fragen?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/note"));

        assertThat(notes.all()).singleElement().satisfies(written -> {
            assertThat(written.title()).isEqualTo("Testcontainers-Abend?");
            assertThat(written.text()).isEqualTo("Wen fragen?");
            // The stamp comes from the use case, never from the form.
            assertThat(written.writtenAt()).isAfter(before);
        });
    }

    @Test
    void theTextMayStayEmpty() throws Exception {
        mvc.perform(post("/note").param("title", "Nur ein Stichwort").param("text", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(notes.all()).singleElement().extracting(Note::text).isNull();
    }

    @Test
    void withoutAStichwortTheFormComesBackWithWhatWasTyped() throws Exception {
        String html = mvc.perform(post("/note").param("title", "  ").param("text", "Wen fragen?"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Stichwort");
        assertThat(page.selectFirst("input[name=text]").val()).isEqualTo("Wen fragen?");
        assertThat(notes.all()).isEmpty();
    }

    @Test
    void theBoardShowsTheSlipsNewestFirst() throws Exception {
        notes.add("Zuerst", null);
        notes.add("Danach", "Mit Text");

        Document page = Jsoup.parse(mvc.perform(get("/note")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#note-board .tile h3").eachText())
                .containsExactly("Danach", "Zuerst");
        assertThat(page.select("#note-board .tile").getFirst().text()).contains("Mit Text");
    }

    @Test
    void anEmptyBoardSaysSo() throws Exception {
        Document page = Jsoup.parse(mvc.perform(get("/note"))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#note-board p.hint").text()).isEqualTo("Noch nichts notiert.");
        assertThat(page.select("#note-board .tile")).isEmpty();
    }

    @Test
    void aSlipIsThrownAwayAndTheBoardComesBackWithoutIt() throws Exception {
        Long id = notes.add("Doch nichts", null).id();
        notes.add("Bleibt", null);

        String fragment = mvc.perform(post("/note/" + id + "/remove"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#note-board .tile h3").eachText())
                .containsExactly("Bleibt");
        assertThat(notes.all()).extracting(Note::title).containsExactly("Bleibt");
    }

    /** A slip that is already gone is not an error — the page just comes back without it. */
    @Test
    void throwingAwayWhatIsAlreadyGoneIsQuiet() throws Exception {
        notes.add("Bleibt", null);

        mvc.perform(post("/note/999/remove")).andExpect(status().isOk());

        assertThat(notes.all()).hasSize(1);
    }

    @Test
    void anHtmxRequestGetsTheBareBoardAndNoPageAroundIt() throws Exception {
        notes.add("Ein Stichwort", null);

        String fragment = mvc.perform(post("/note").header("HX-Request", "true")
                        .param("title", "Noch eins").param("text", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        assertThat(Jsoup.parseBodyFragment(fragment).select("#note-board .tile h3").eachText())
                .containsExactly("Noch eins", "Ein Stichwort");
    }

    // --- changing a slip -------------------------------------------------------------

    @Test
    void theTileOpensAsAnEditorWithWhatIsStoredInIt() throws Exception {
        Long id = notes.add("Testcontainers-Abend?", "Wen fragen?").id();

        String fragment = mvc.perform(get("/note/" + id + "/edit"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("input[name=title]").val()).isEqualTo("Testcontainers-Abend?");
        assertThat(tile.selectFirst("textarea[name=text]").val()).isEqualTo("Wen fragen?");
        // One tile comes back, not the board around it.
        assertThat(tile.select("#note-board")).isEmpty();
        assertThat(tile.select("section.tile")).hasSize(1);
    }

    @Test
    void aChangeIsStoredAndTheTileComesBackAsACard() throws Exception {
        Long id = notes.add("Testcontainers-Abend?", "Wen fragen?").id();

        String fragment = mvc.perform(post("/note/" + id)
                        .param("title", "Testcontainers-Abend")
                        .param("text", "Anna fragen"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("h3").text()).isEqualTo("Testcontainers-Abend");
        assertThat(tile.select("form")).isEmpty();
        assertThat(notes.all()).singleElement().satisfies(stored -> {
            assertThat(stored.title()).isEqualTo("Testcontainers-Abend");
            assertThat(stored.text()).isEqualTo("Anna fragen");
        });
    }

    /** The stamp says when the note was written, not when it was last touched. */
    @Test
    void aChangeDoesNotReDateTheNote() throws Exception {
        Long id = notes.add("Testcontainers-Abend?", null).id();
        LocalDateTime written = notes.byId(id).orElseThrow().writtenAt();

        mvc.perform(post("/note/" + id).param("title", "Anders").param("text", ""))
                .andExpect(status().isOk());

        assertThat(notes.byId(id).orElseThrow().writtenAt()).isEqualTo(written);
    }

    @Test
    void aChangeWithoutAStichwortComesBackAsTheEditor() throws Exception {
        Long id = notes.add("Testcontainers-Abend?", "Wen fragen?").id();

        String fragment = mvc.perform(post("/note/" + id)
                        .param("title", "   ").param("text", "Anna fragen"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("p.error").text()).contains("Stichwort");
        // What was typed is still there, next to the title that could not be taken away.
        assertThat(tile.selectFirst("textarea[name=text]").val()).isEqualTo("Anna fragen");
        assertThat(notes.byId(id).orElseThrow().text()).isEqualTo("Wen fragen?");
    }

    @Test
    void abbrechenAsksForTheCardAgain() throws Exception {
        Long id = notes.add("Testcontainers-Abend?", null).id();

        String fragment = mvc.perform(get("/note/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("h3").text()).isEqualTo("Testcontainers-Abend?");
        assertThat(tile.select("form")).isEmpty();
    }

    /** Thrown away in another tab: the tile says so instead of coming back empty. */
    @Test
    void aTileThatIsGoneSaysSo() throws Exception {
        String fragment = mvc.perform(get("/note/999/edit"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("gibt es nicht mehr");
    }

    @Test
    void theSlipBoxIsInTheNavigation() throws Exception {
        Document page = Jsoup.parse(mvc.perform(get("/note"))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst(".topbar nav a.here").text()).isEqualTo("Zettelkasten");
    }
}
