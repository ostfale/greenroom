package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole slice: browser request, controller, use case, real Postgres — and back as
 * rendered HTML. What is asserted is what the page actually shows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpeakerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageSpeakers speakers;

    @Autowired
    private SpeakerRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAll();
    }

    @Test
    void theFormPostsANewSpeakerIntoTheDatabase() throws Exception {
        mvc.perform(post("/speaker")
                        .param("name", "Max Muster")
                        .param("email", "max@example.org")
                        .param("company", "Musterfirma GmbH")
                        .param("phone", "")
                        .param("bio", "Schreibt Java, seit es Generics gibt.")
                        .param("notes", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/speaker"));

        assertThat(speakers.all()).singleElement().satisfies(stored -> {
            assertThat(stored.name()).isEqualTo("Max Muster");
            assertThat(stored.email()).isEqualTo("max@example.org");
            assertThat(stored.company()).isEqualTo("Musterfirma GmbH");
            assertThat(stored.phone()).isNull();
        });
    }

    @Test
    void aSpeakerWithoutAnAddressComesBackToTheFormWithWhatWasTyped() throws Exception {
        String html = mvc.perform(post("/speaker")
                        .param("name", "Max Muster")
                        .param("email", "")
                        .param("company", "Musterfirma GmbH")
                        .param("phone", "")
                        .param("bio", "")
                        .param("notes", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("E-Mail");
        assertThat(page.selectFirst("input[name=name]").val()).isEqualTo("Max Muster");
        assertThat(page.selectFirst("input[name=company]").val()).isEqualTo("Musterfirma GmbH");
        assertThat(repository.count()).isZero();
    }

    @Test
    void theListShowsEverySpeakerAlphabetically() throws Exception {
        speakers.add(Speaker.of("Zoe Zimmer", "zoe@example.org"));
        speakers.add(Speaker.of("Anna Albers", "anna@example.org"));

        String html = mvc.perform(get("/speaker"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#speaker-table tbody tr td:first-child").eachText())
                .containsExactly("Anna Albers", "Zoe Zimmer");
    }

    @Test
    void theSearchNarrowsTheList() throws Exception {
        speakers.add(Speaker.of("Zoe Zimmer", "zoe@example.org"));
        speakers.add(Speaker.of("Anna Albers", "anna@example.org"));

        String html = mvc.perform(get("/speaker").param("search", "albers"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#speaker-table tbody tr td:first-child").eachText())
                .containsExactly("Anna Albers");
    }

    @Test
    void anHtmxRequestGetsTheBareTableAndNoPageAroundIt() throws Exception {
        speakers.add(Speaker.of("Anna Albers", "anna@example.org"));

        String fragment = mvc.perform(get("/speaker").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<table").doesNotContain("<html").doesNotContain("<header");
        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("table#speaker-table")).isNotNull();
        assertThat(parsed.select("tbody tr td:first-child").eachText()).containsExactly("Anna Albers");
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String html = mvc.perform(get("/speaker")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#speaker-table caption").text())
                .isEqualTo("Keine Referenten gefunden.");
    }
}
