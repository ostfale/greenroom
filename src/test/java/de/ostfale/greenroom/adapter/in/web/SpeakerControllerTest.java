package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.springframework.test.web.servlet.MockMvc;

import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole slice: browser request, controller, use case, real Postgres — and back as
 * rendered HTML. What is asserted is what the page actually shows.
 */
@WebTest
class SpeakerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageSpeakers speakers;

    @Autowired
    private SpeakerRepository repository;

    @Autowired
    private ManageEvents events;

    /** A real picture — the scaler reads the bytes, it does not trust a content type. */
    private static byte[] picture(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private static BufferedImage read(byte[] data) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheTable() {
        database.empty();
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

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("div#speaker-table table")).isNotNull();
        assertThat(parsed.select("tbody tr td:first-child").eachText()).containsExactly("Anna Albers");
    }

    @Test
    void aSpeakerCanBringAPictureAlongWhenCreated() throws Exception {
        mvc.perform(multipart("/speaker")
                        .file(new MockMultipartFile("photo", "max.png", "image/png", picture(80, 80)))
                        .param("name", "Max Muster")
                        .param("email", "max@example.org")
                        .param("company", "")
                        .param("phone", "")
                        .param("bio", "")
                        .param("notes", ""))
                .andExpect(redirectedUrl("/speaker"));

        Long id = speakers.all().getFirst().id();
        assertThat(speakers.photoOf(id)).isPresent();
        mvc.perform(get("/speaker/{id}/photo", id))
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void creatingWithoutAPictureIsStillTheNormalCase() throws Exception {
        mvc.perform(multipart("/speaker")
                        .file(new MockMultipartFile("photo", "", "application/octet-stream", new byte[0]))
                        .param("name", "Max Muster")
                        .param("email", "max@example.org")
                        .param("company", "")
                        .param("phone", "")
                        .param("bio", "")
                        .param("notes", ""))
                .andExpect(redirectedUrl("/speaker"));

        assertThat(speakers.all()).hasSize(1);
        assertThat(speakers.photoOf(speakers.all().getFirst().id())).isEmpty();
    }

    @Test
    void aRefusedPictureLeavesNoHalfEnteredSpeakerBehind() throws Exception {
        String html = mvc.perform(multipart("/speaker")
                        .file(new MockMultipartFile("photo", "v.pdf", "application/pdf", new byte[]{1}))
                        .param("name", "Max Muster")
                        .param("email", "max@example.org")
                        .param("company", "")
                        .param("phone", "")
                        .param("bio", "")
                        .param("notes", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text())
                .contains("JPEG")
                .contains("nicht angelegt");
        assertThat(page.selectFirst("input[name=name]").val()).isEqualTo("Max Muster");
        assertThat(speakers.all()).isEmpty();
    }

    // --- changing and removing --------------------------------------------------------

    @Test
    void theDetailFormWritesTheChangedFieldsBack() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        mvc.perform(post("/speaker/{id}", id)
                        .param("name", "Max Mustermann")
                        .param("email", "neu@example.org")
                        .param("company", "Nordsee GmbH")
                        .param("phone", "040 999")
                        .param("bio", "Neue Vita.")
                        .param("notes", "Ruft lieber an."))
                .andExpect(status().isOk());

        assertThat(speakers.byId(id)).get().satisfies(stored -> {
            assertThat(stored.name()).isEqualTo("Max Mustermann");
            assertThat(stored.email()).isEqualTo("neu@example.org");
            assertThat(stored.company()).isEqualTo("Nordsee GmbH");
            assertThat(stored.bio()).isEqualTo("Neue Vita.");
            assertThat(stored.notes()).isEqualTo("Ruft lieber an.");
        });
    }

    @Test
    void aChangeThatBreaksTheRulesSaysSoAndChangesNothing() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        String fragment = mvc.perform(post("/speaker/{id}", id)
                        .param("name", "Max Muster")
                        .param("email", "")
                        .param("company", "")
                        .param("phone", "")
                        .param("bio", "")
                        .param("notes", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("E-Mail-Adresse");
        assertThat(speakers.byId(id).orElseThrow().email()).isEqualTo("max@example.org");
    }

    @Test
    void aSpeakerWhoNeverSpokeCanBeRemoved() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        mvc.perform(post("/speaker/{id}/remove", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/speaker"));

        assertThat(speakers.byId(id)).isEmpty();
    }

    @Test
    void aSpeakerAnnouncedOnATalkStays() throws Exception {
        Long id = speakers.add(aSpeaker()).id();
        events.add(Event.draftFor(Talk.by(TalkSpeaker.of(id)).withTitle("Records in Java 25")));

        String fragment = mvc.perform(post("/speaker/{id}/remove", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("angekündigt");
        assertThat(speakers.byId(id)).isPresent();
    }

    @Test
    void removingASpeakerTakesThePictureWithThem() throws Exception {
        Long id = speakers.add(aSpeaker()).id();
        mvc.perform(multipart("/speaker/{id}/photo", id)
                .file(new MockMultipartFile("photo", "max.png", "image/png", picture(80, 80))));

        mvc.perform(post("/speaker/{id}/remove", id)).andExpect(redirectedUrl("/speaker"));

        assertThat(speakers.photoOf(id)).isEmpty();
    }

    // --- the detail page and its picture ---------------------------------------------

    @Test
    void theListLinksToTheDetailPage() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        String html = mvc.perform(get("/speaker")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#speaker-table tbody tr td a").attr("href"))
                .isEqualTo("/speaker/" + id);
    }

    @Test
    void theDetailPageShowsWhatIsKnownAboutTheSpeaker() throws Exception {
        Long id = speakers.add(Speaker.of("Max Muster", "max@example.org")
                .withContact("Musterfirma GmbH", "max@example.org", "040 123456")
                .withBio("Schreibt Java, seit es Generics gibt.")).id();

        String html = mvc.perform(get("/speaker/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("h1").text()).isEqualTo("Max Muster");
        // The detail page is the edit form: the fields carry what is stored.
        assertThat(page.selectFirst("#speaker-fields input[name=name]").val()).isEqualTo("Max Muster");
        assertThat(page.selectFirst("#speaker-fields input[name=email]").val()).isEqualTo("max@example.org");
        assertThat(page.selectFirst("#speaker-fields input[name=company]").val()).isEqualTo("Musterfirma GmbH");
        assertThat(page.selectFirst("#speaker-fields input[name=phone]").val()).isEqualTo("040 123456");
        assertThat(page.selectFirst("#speaker-fields textarea[name=bio]").text())
                .isEqualTo("Schreibt Java, seit es Generics gibt.");
        assertThat(page.selectFirst(".portrait").hasClass("placeholder")).isTrue();
        assertThat(page.selectFirst(".portrait").text()).isEqualTo("M");
    }

    @Test
    void anUnknownSpeakerSendsYouBackToTheList() throws Exception {
        mvc.perform(get("/speaker/{id}", 999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/speaker"));
    }

    @Test
    void anUploadedPictureIsServedBackWithItsOwnContentType() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        String fragment = mvc.perform(multipart("/speaker/{id}/photo", id)
                        .file(new MockMultipartFile("photo", "max.png", "image/png", picture(1600, 800))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("img.portrait")).isNotNull();

        byte[] served = mvc.perform(get("/speaker/{id}/photo", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();

        // Shrunk on the way in: 1600 pixels went in, 600 come back.
        assertThat(read(served).getWidth()).isEqualTo(600);
        assertThat(read(served).getHeight()).isEqualTo(300);
    }

    @Test
    void aSecondUploadTakesThePlaceOfTheFirst() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        mvc.perform(multipart("/speaker/{id}/photo", id)
                .file(new MockMultipartFile("photo", "alt.png", "image/png", picture(100, 100))));
        mvc.perform(multipart("/speaker/{id}/photo", id)
                .file(new MockMultipartFile("photo", "neu.png", "image/png", picture(200, 100))));

        byte[] served = mvc.perform(get("/speaker/{id}/photo", id))
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(read(served).getWidth()).isEqualTo(200);
        assertThat(speakers.photoOf(id)).isPresent();
    }

    @Test
    void aFileThatIsNoPictureSaysSoAndChangesNothing() throws Exception {
        Long id = speakers.add(aSpeaker()).id();

        String fragment = mvc.perform(multipart("/speaker/{id}/photo", id)
                        .file(new MockMultipartFile("photo", "vertrag.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("JPEG");
        assertThat(speakers.photoOf(id)).isEmpty();
    }

    @Test
    void aPictureCanBeTakenAwayAgain() throws Exception {
        Long id = speakers.add(aSpeaker()).id();
        mvc.perform(multipart("/speaker/{id}/photo", id)
                .file(new MockMultipartFile("photo", "max.png", "image/png", picture(80, 80))));

        String fragment = mvc.perform(post("/speaker/{id}/photo/remove", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst(".portrait").hasClass("placeholder")).isTrue();
        assertThat(speakers.photoOf(id)).isEmpty();
        mvc.perform(get("/speaker/{id}/photo", id)).andExpect(status().isNotFound());
    }

    @Test
    void deletingASpeakerTakesThePictureWithIt() throws Exception {
        Long id = speakers.add(aSpeaker()).id();
        mvc.perform(multipart("/speaker/{id}/photo", id)
                .file(new MockMultipartFile("photo", "max.png", "image/png", picture(80, 80))));

        repository.deleteById(id);

        assertThat(speakers.photoOf(id)).isEmpty();
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String html = mvc.perform(get("/speaker")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#speaker-table caption").text())
                .isEqualTo("Keine Referenten gefunden.");
    }
}
