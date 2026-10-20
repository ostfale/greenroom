package de.ostfale.greenroom;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A changed stylesheet has to reach the browser with the next deploy. Without a cache header
 * the browser guesses how long it may keep the old one, and a fixed layout went on looking
 * broken until the cache was cleared by hand. So every file names its content in its path:
 * a new content is a new address, and the old one may be kept for good.
 */
@WebTest
class StaticFilesAreVersionedTest {

    private static final String HASH = "-[0-9a-f]{32}";

    @Autowired
    private MockMvc mvc;

    @Test
    void thePageLinksItsFilesByContent() throws Exception {
        Document page = Jsoup.parse(mvc.perform(get("/"))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("link[rel=stylesheet]").attr("href"))
                .matches("/css/greenroom" + HASH + "\\.css");
        assertThat(page.select("script[src]").eachAttr("src"))
                .allSatisfy(src -> assertThat(src).matches(".*" + HASH + "\\.js"));
    }

    @Test
    void aVersionedFileIsKeptForAYear() throws Exception {
        String href = Jsoup.parse(mvc.perform(get("/"))
                        .andReturn().getResponse().getContentAsString())
                .selectFirst("link[rel=stylesheet]").attr("href");

        MvcResult css = mvc.perform(get(href)).andExpect(status().isOk()).andReturn();

        assertThat(css.getResponse().getHeader("Cache-Control")).contains("max-age=31536000");
        // The fonts the stylesheet names are versioned the same way.
        assertThat(css.getResponse().getContentAsString())
                .containsPattern("/fonts/archivo-latin-wght-normal" + HASH + "\\.woff2");
    }
}
