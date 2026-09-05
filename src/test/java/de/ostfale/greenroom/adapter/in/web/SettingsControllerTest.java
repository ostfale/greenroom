package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.tags.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole slice: browser request, controller, use case, real Postgres — and back as
 * rendered HTML. What is asserted is what the page actually shows.
 */
@WebTest
class SettingsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageTags tags;

    @Autowired
    private ManageEvents events;

    @Autowired
    private ManageSpeakers speakers;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheList() {
        database.empty();
    }

    @Test
    void theFormPutsANewTagOnTheList() throws Exception {
        mvc.perform(post("/settings/tag").param("name", "Spring"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        assertThat(tags.all()).extracting(Tag::name).containsExactly("Spring");
    }

    @Test
    void theSameWordDoesNotGetOnTheListTwiceHoweverItIsSpelled() throws Exception {
        tags.add(Tag.named("Spring"));

        String html = mvc.perform(post("/settings/tag").param("name", "spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("steht schon auf der Liste");
        assertThat(page.selectFirst("form.new input[name=name]").val()).isEqualTo("spring");
        assertThat(tags.all()).hasSize(1);
    }

    @Test
    void aTagNeedsAWord() throws Exception {
        String html = mvc.perform(post("/settings/tag").param("name", "  "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("Tag");
        assertThat(tags.all()).isEmpty();
    }

    @Test
    void theListIsAlphabetical() throws Exception {
        tags.add(Tag.named("Testing"));
        tags.add(Tag.named("Architektur"));
        tags.add(Tag.named("Spring"));

        String html = mvc.perform(get("/settings")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#tag-list ul.tags li").eachText())
                .containsExactly("Architektur", "Spring", "Testing");
    }

    @Test
    void anEmptyListSaysSo() throws Exception {
        String html = mvc.perform(get("/settings")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#tag-list p.hint").text())
                .isEqualTo("Noch keine Tags angelegt.");
    }

    @Test
    void anHtmxPostGetsTheBareListBackWithTheNewTagInIt() throws Exception {
        tags.add(Tag.named("Architektur"));

        String fragment = mvc.perform(post("/settings/tag")
                        .param("name", "Spring")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        assertThat(Jsoup.parseBodyFragment(fragment).select("#tag-list ul.tags li").eachText())
                .containsExactly("Architektur", "Spring");
    }

    @Test
    void anHtmxPostThatIsRefusedShowsTheReasonAndLeavesTheListAlone() throws Exception {
        tags.add(Tag.named("Spring"));

        String fragment = mvc.perform(post("/settings/tag")
                        .param("name", "SPRING")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("p.error").text()).contains("steht schon auf der Liste");
        assertThat(parsed.select("#tag-list ul.tags li").eachText()).containsExactly("Spring");
    }

    // --- keeping the list -------------------------------------------------------------

    @Test
    void aTagIsRenamedInPlace() throws Exception {
        Long id = tags.add(Tag.named("Sprint")).id();

        String fragment = mvc.perform(post("/settings/tag/" + id).param("name", "Spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#tag-list ul.tags li").eachText())
                .containsExactly("Spring");
        assertThat(tags.all()).extracting(Tag::name).containsExactly("Spring");
    }

    @Test
    void aTagIsNotRenamedOntoAWordThatIsAlreadyThere() throws Exception {
        tags.add(Tag.named("Spring"));
        Long id = tags.add(Tag.named("Architektur")).id();

        String fragment = mvc.perform(post("/settings/tag/" + id).param("name", "spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("steht schon auf der Liste");
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("input[name=name]").val())
                .isEqualTo("spring");
        assertThat(tags.all()).extracting(Tag::name).containsExactly("Architektur", "Spring");
    }

    /** Saving a tag without touching it is not a duplicate of itself. */
    @Test
    void aTagMayKeepItsOwnWord() throws Exception {
        Long id = tags.add(Tag.named("Spring")).id();

        String fragment = mvc.perform(post("/settings/tag/" + id).param("name", "Spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("p.error")).isEmpty();
        assertThat(tags.all()).extracting(Tag::name).containsExactly("Spring");
    }

    @Test
    void aTagIsTakenOffTheList() throws Exception {
        Long id = tags.add(Tag.named("Spring")).id();

        String fragment = mvc.perform(post("/settings/tag/" + id + "/remove"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("#tag-list p.hint").text())
                .contains("Noch keine Tags");
        assertThat(tags.all()).isEmpty();
    }

    @Test
    void aTagThatIsGoneSaysSoInsteadOfFailing() throws Exception {
        String fragment = mvc.perform(post("/settings/tag/404").param("name", "Spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("gibt es nicht mehr");
    }

    /**
     * The point of storing words instead of references: what an evening was announced with
     * survives whatever happens to the list afterwards.
     */
    @Test
    void renamingAndRemovingLeaveTheEveningsAlone() throws Exception {
        Long speakerId = speakers.add(aSpeaker()).id();
        Long renamed = tags.add(Tag.named("Sprint")).id();
        Long dropped = tags.add(Tag.named("Architektur")).id();
        Long eventId = events.add(Event.draftFor(
                aReadyTalk(speakerId).withTags(List.of("Sprint", "Architektur")))).id();

        mvc.perform(post("/settings/tag/" + renamed).param("name", "Spring"))
                .andExpect(status().isOk());
        mvc.perform(post("/settings/tag/" + dropped + "/remove")).andExpect(status().isOk());

        assertThat(events.byId(eventId).orElseThrow().tags())
                .containsExactly("Sprint", "Architektur");
    }

    @Test
    void theListIsARowOfChipsAndNotAColumnOfForms() throws Exception {
        tags.add(Tag.named("Spring"));
        tags.add(Tag.named("Architektur"));

        String html = mvc.perform(get("/settings")).andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#tag-list ul.tags li button.badge").eachText())
                .containsExactly("Architektur", "Spring");
        assertThat(page.select("#tag-list form")).isEmpty();
        assertThat(page.selectFirst("#tag-editor").children()).isEmpty();
    }

    @Test
    void pickingAWordOpensItUnderTheList() throws Exception {
        Long id = tags.add(Tag.named("Spring")).id();

        String fragment = mvc.perform(get("/settings/tag/" + id).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document editor = Jsoup.parseBodyFragment(fragment);
        assertThat(editor.selectFirst("#tag-editor input[name=name]").val()).isEqualTo("Spring");
        assertThat(editor.selectFirst("form").attr("action")).isEqualTo("/settings/tag/" + id);
    }

    @Test
    void pickingAWordThatIsGoneSaysSoInsteadOfOpeningAnything() throws Exception {
        String fragment = mvc.perform(get("/settings/tag/404").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document editor = Jsoup.parseBodyFragment(fragment);
        assertThat(editor.selectFirst("p.error").text()).contains("gibt es nicht mehr");
        assertThat(editor.select("form")).isEmpty();
    }

    /** "Abbrechen" asks for the bare list again, which closes the editor with it. */
    @Test
    void theBareListComesBackForHtmx() throws Exception {
        tags.add(Tag.named("Spring"));

        String fragment = mvc.perform(get("/settings").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        assertThat(Jsoup.parseBodyFragment(fragment).select("#tag-list ul.tags li").eachText())
                .containsExactly("Spring");
    }

    @Test
    void theSettingsPageSaysWhichVersionIsAnsweringAndWhoToAsk() throws Exception {
        String html = mvc.perform(get("/settings")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("#about p").text())
                .matches("greenroom Version \\d+\\.\\d+\\.\\d+");
        assertThat(page.selectFirst("#about a[href^=mailto:]").attr("href"))
                .isEqualTo("mailto:info@uwe-sauerbrei.de");
    }

    /** The refusal renders the whole page again, and the tile is part of it. */
    @Test
    void aRefusedTagLeavesTheVersionOnThePage() throws Exception {
        tags.add(Tag.named("Spring"));

        String html = mvc.perform(post("/settings/tag").param("name", "spring"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#about p").text())
                .startsWith("greenroom Version ");
    }

}
