package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.location.Address;
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
            assertThat(stored.addresses()).singleElement()
                    .satisfies(address -> assertThat(address.active()).isTrue());
            assertThat(stored.currentCapacity()).isEqualTo(80);
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
            assertThat(stored.currentCapacity()).isNull();
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
        assertThat(page.selectFirst("p.error").text()).contains("Plätze");
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
    void seatsWithoutAnAddressAreRefusedBecauseTheyBelongToOne() throws Exception {
        String html = mvc.perform(post("/location")
                        .param("name", "Musterfirma GmbH")
                        .param("street", "")
                        .param("postalCode", "")
                        .param("city", "")
                        .param("capacity", "80")
                        .param("notes", "")
                        .param("contactName", "Max Muster")
                        .param("contactEmail", "max@example.org")
                        .param("contactPhone", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("Adresse");
        assertThat(repository.count()).isZero();
    }

    @Test
    void aNewAddressBringsItsOwnNumberOfSeats() throws Exception {
        Long id = locations.add(Location.of("Kuehne + Nagel", HOST)
                .movedTo(Address.at("Grosser Grasbrook 11", "20457", "Hamburg").withCapacity(40))).id();

        mvc.perform(post("/location/{id}/address", id)
                        .param("street", "Neuer Weg 2")
                        .param("postalCode", "20095")
                        .param("city", "Hamburg")
                        .param("capacity", "120")
                        .param("moved", "true"))
                .andExpect(status().isOk());

        Location moved = locations.byId(id).orElseThrow();
        assertThat(moved.currentCapacity()).isEqualTo(120);
        assertThat(moved.addresses().getFirst().capacity()).isEqualTo(40);
    }

    @Test
    void theDetailPageShowsEveryAddressAndWhichOneCountsNow() throws Exception {
        Long id = locations.add(Location.of("Kuehne + Nagel", HOST)
                .withAddress("Grosser Grasbrook 11", "20457", "Hamburg")
                .movedTo(Address.at("Neuer Weg 2", "20095", "Hamburg"))).id();

        String html = mvc.perform(get("/location/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#address-list tbody tr td:first-child").eachText())
                .containsExactly("Grosser Grasbrook 11, 20457 Hamburg", "Neuer Weg 2, 20095 Hamburg");
        assertThat(page.select("#address-list tbody tr td:nth-child(3)").eachText())
                .containsExactly("ehemalig", "aktiv");
    }

    @Test
    void anUnknownLocationSendsYouBackToTheList() throws Exception {
        mvc.perform(get("/location/{id}", 999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/location"));
    }

    @Test
    void aMoveRetiresTheEarlierAddressAndComesBackAsTheBareList() throws Exception {
        Long id = locations.add(Location.of("Kuehne + Nagel", HOST)
                .withAddress("Grosser Grasbrook 11", "20457", "Hamburg")).id();

        String fragment = mvc.perform(post("/location/{id}/address", id)
                        .param("street", "Neuer Weg 2")
                        .param("postalCode", "20095")
                        .param("city", "Hamburg")
                        .param("moved", "true")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html");
        assertThat(Jsoup.parseBodyFragment(fragment).select("#address-list tbody tr td:nth-child(3)").eachText())
                .containsExactly("ehemalig", "aktiv");
        assertThat(locations.byId(id).orElseThrow().addressLine())
                .isEqualTo("Neuer Weg 2, 20095 Hamburg");
    }

    @Test
    void withoutTheMovedFlagTheSecondAddressIsASecondSite() throws Exception {
        Long id = locations.add(Location.of("Musterfirma GmbH", HOST)
                .withAddress("Musterweg 1", "22179", "Hamburg")).id();

        mvc.perform(post("/location/{id}/address", id)
                        .param("street", "Zweigweg 5")
                        .param("postalCode", "21073")
                        .param("city", "Hamburg")
                        .param("moved", "false"))
                .andExpect(status().isOk());

        assertThat(locations.byId(id).orElseThrow().activeAddresses()).hasSize(2);
    }

    @Test
    void anAddressWithoutStreetOrTownSaysSoAndChangesNothing() throws Exception {
        Long id = locations.add(Location.of("Musterfirma GmbH", HOST)
                .withAddress("Musterweg 1", "22179", "Hamburg")).id();

        String fragment = mvc.perform(post("/location/{id}/address", id)
                        .param("street", "")
                        .param("postalCode", "")
                        .param("city", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Stadt");
        assertThat(locations.byId(id).orElseThrow().addresses()).hasSize(1);
    }

    @Test
    void anAddressCanBeStilledAndWokenUpAgain() throws Exception {
        Long id = locations.add(Location.of("Musterfirma GmbH", HOST)
                .withAddress("Musterweg 1", "22179", "Hamburg")).id();

        mvc.perform(post("/location/{id}/address/{position}", id, 0).param("active", "false"))
                .andExpect(status().isOk());
        assertThat(locations.byId(id).orElseThrow().activeAddresses()).isEmpty();

        mvc.perform(post("/location/{id}/address/{position}", id, 0).param("active", "true"))
                .andExpect(status().isOk());
        assertThat(locations.byId(id).orElseThrow().activeAddresses()).hasSize(1);
    }

    @Test
    void theListLinksToTheDetailPage() throws Exception {
        Long id = locations.add(Location.of("Musterfirma GmbH", HOST)).id();

        String html = mvc.perform(get("/location")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#location-table tbody tr td a").attr("href"))
                .isEqualTo("/location/" + id);
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
