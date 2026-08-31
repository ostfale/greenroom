package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.application.port.out.TagRepository;
import de.ostfale.greenroom.domain.tag.Tag;
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
class SettingsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageTags tags;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void emptyTheList() {
        tagRepository.deleteAll();
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
        assertThat(page.selectFirst("input[name=name]").val()).isEqualTo("spring");
        assertThat(tags.all()).hasSize(1);
    }

    @Test
    void aTagNeedsAWord() throws Exception {
        String html = mvc.perform(post("/settings/tag").param("name", "  "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("Schlagwort");
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
                .isEqualTo("Noch keine Schlagwörter angelegt.");
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
}
