package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.location.ContactPerson;
import de.ostfale.greenroom.domain.location.Location;
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
class LocationControllerTest {

    private static final ContactPerson HOST = ContactPerson.of("Max Muster", "max@example.org");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageLocations locations;

    @Autowired
    private LocationRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAll();
    }

    @Test
    void theFormPostsANewLocationWithItsContactIntoTheDatabase() throws Exception {
        mvc.perform(post("/location")
                        .param("name", "Musterfirma GmbH")
                        .param("street", "Musterweg 1")
                        .param("postalCode", "22179")
                        .param("city", "Hamburg")
                        .param("capacity", "80")
                        .param("notes", "Parken im Hof.")
                        .param("contactName", "Max Muster")
                        .param("contactEmail", "max@example.org")
                        .param("contactPhone", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/location"));

        assertThat(locations.all()).singleElement().satisfies(stored -> {
            assertThat(stored.name()).isEqualTo("Musterfirma GmbH");
            assertThat(stored.addressLine()).isEqualTo("Musterweg 1, 22179 Hamburg");
            assertThat(stored.capacity()).isEqualTo(80);
            assertThat(stored.contacts()).singleElement().satisfies(contact -> {
                assertThat(contact.name()).isEqualTo("Max Muster");
                assertThat(contact.email()).isEqualTo("max@example.org");
                assertThat(contact.phone()).isNull();
            });
        });
    }

    @Test
    void aLocationWithoutACapacityIsStoredWithoutOne() throws Exception {
        mvc.perform(post("/location")
                        .param("name", "Musterfirma GmbH")
                        .param("street", "")
                        .param("postalCode", "")
                        .param("city", "")
                        .param("capacity", "")
                        .param("notes", "")
                        .param("contactName", "Max Muster")
                        .param("contactEmail", "max@example.org")
                        .param("contactPhone", ""))
                .andExpect(redirectedUrl("/location"));

        assertThat(locations.all()).singleElement().satisfies(stored -> {
            assertThat(stored.capacity()).isNull();
            assertThat(stored.addressLine()).isEmpty();
        });
    }

    @Test
    void aLocationWithoutAContactComesBackToTheFormWithWhatWasTyped() throws Exception {
        String html = mvc.perform(post("/location")
                        .param("name", "Musterfirma GmbH")
                        .param("street", "Musterweg 1")
                        .param("postalCode", "")
                        .param("city", "Hamburg")
                        .param("capacity", "")
                        .param("notes", "")
                        .param("contactName", "")
                        .param("contactEmail", "")
                        .param("contactPhone", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Ansprechpartner");
        assertThat(page.selectFirst("input[name=name]").val()).isEqualTo("Musterfirma GmbH");
        assertThat(page.selectFirst("input[name=street]").val()).isEqualTo("Musterweg 1");
        assertThat(page.selectFirst("input[name=city]").val()).isEqualTo("Hamburg");
        assertThat(repository.count()).isZero();
    }

    @Test
    void aCapacityThatIsNotANumberSaysSoInsteadOfFailing() throws Exception {
        String html = mvc.perform(post("/location")
                        .param("name", "Musterfirma GmbH")
                        .param("street", "")
                        .param("postalCode", "")
                        .param("city", "")
                        .param("capacity", "viele")
                        .param("notes", "")
                        .param("contactName", "Max Muster")
                        .param("contactEmail", "max@example.org")
                        .param("contactPhone", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Kapazität");
        assertThat(page.selectFirst("input[name=capacity]").val()).isEqualTo("viele");
        assertThat(repository.count()).isZero();
    }

    @Test
    void theListShowsEveryLocationAlphabeticallyWithItsAddress() throws Exception {
        locations.add(Location.of("Zeise Kinos", HOST).withAddress("Friedensallee 7", "22765", "Hamburg"));
        locations.add(Location.of("Adobe Hamburg", HOST));

        String html = mvc.perform(get("/location"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#location-table tbody tr td:first-child").eachText())
                .containsExactly("Adobe Hamburg", "Zeise Kinos");
        assertThat(page.select("#location-table tbody tr td:nth-child(2)").eachText())
                .containsExactly("—", "Friedensallee 7, 22765 Hamburg");
    }

    @Test
    void theSearchNarrowsTheList() throws Exception {
        locations.add(Location.of("Zeise Kinos", HOST).withAddress(null, null, "Hamburg"));
        locations.add(Location.of("Adobe", HOST).withAddress(null, null, "Lüneburg"));

        String html = mvc.perform(get("/location").param("search", "zeise"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#location-table tbody tr td:first-child").eachText())
                .containsExactly("Zeise Kinos");
    }

    @Test
    void anHtmxRequestGetsTheBareTableAndNoPageAroundIt() throws Exception {
        locations.add(Location.of("Zeise Kinos", HOST));

        String fragment = mvc.perform(get("/location").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<table").doesNotContain("<html").doesNotContain("<header");
        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("table#location-table")).isNotNull();
        assertThat(parsed.select("tbody tr td:first-child").eachText()).containsExactly("Zeise Kinos");
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String html = mvc.perform(get("/location")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#location-table caption").text())
                .isEqualTo("Keine Orte gefunden.");
    }
}
