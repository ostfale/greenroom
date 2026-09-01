package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static de.ostfale.greenroom.Fixtures.aContact;
import static de.ostfale.greenroom.Fixtures.aLocation;
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
class LocationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageLocations locations;

    @Autowired
    private LocationRepository repository;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheTable() {
        database.empty();
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
        locations.add(Location.of("Zeise Kinos", aContact()).withAddress("Friedensallee 7", "22765", "Hamburg"));
        locations.add(Location.of("Adobe Hamburg", aContact()));

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
        Long id = locations.add(Location.of("Kuehne + Nagel", aContact())
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
        Long id = locations.add(Location.of("Kuehne + Nagel", aContact())
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
    void anAddressChangeAlsoBringsTheSummaryTileUpToDate() throws Exception {
        Long id = locations.add(aLocation()
                .movedTo(Address.at("Musterweg 1", "22179", "Hamburg").withCapacity(60))).id();

        String fragment = mvc.perform(post("/location/{id}/address", id)
                        .param("street", "")
                        .param("postalCode", "")
                        .param("city", "Lüneburg")
                        .param("capacity", "")
                        .param("moved", "false")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("#address-list")).isNotNull();
        Element summary = parsed.selectFirst("#location-summary");
        assertThat(summary).isNotNull();
        assertThat(summary.attr("hx-swap-oob")).isEqualTo("true");
        assertThat(summary.select("dd").last().text()).isEqualTo("2 aktiv");
    }

    @Test
    void theFieldsForAFurtherAddressStayFoldedAway() throws Exception {
        Long id = locations.add(aLocation()
                .withAddress("Musterweg 1", "22179", "Hamburg")).id();

        String html = mvc.perform(get("/location/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        Element reveal = page.selectFirst("details.reveal");
        assertThat(reveal).isNotNull();
        assertThat(reveal.hasAttr("open")).isFalse();
        assertThat(reveal.selectFirst("summary").text()).isEqualTo("Weitere Adresse");
        // The form is still on the page — it is only out of sight until asked for.
        assertThat(reveal.selectFirst("input[name=street]")).isNotNull();
    }

    @Test
    void anUnknownLocationSendsYouBackToTheList() throws Exception {
        mvc.perform(get("/location/{id}", 999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/location"));
    }

    @Test
    void aMoveRetiresTheEarlierAddressAndComesBackAsTheBareList() throws Exception {
        Long id = locations.add(Location.of("Kuehne + Nagel", aContact())
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
        Long id = locations.add(aLocation()
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
        Long id = locations.add(aLocation()
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
        Long id = locations.add(aLocation()
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
        Long id = locations.add(aLocation()).id();

        String html = mvc.perform(get("/location")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#location-table tbody tr td a").attr("href"))
                .isEqualTo("/location/" + id);
    }

    // --- the contacts on the detail page ---------------------------------------------

    @Test
    void aContactCanBeAddedOnTheDetailPage() throws Exception {
        Long id = locations.add(aLocation()).id();

        String fragment = mvc.perform(post("/location/{id}/contact", id)
                        .param("contactName", "Anna Albers")
                        .param("contactEmail", "anna@example.org")
                        .param("contactPhone", "040 123456"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html");
        assertThat(locations.byId(id).orElseThrow().contacts())
                .extracting(ContactPerson::name)
                .containsExactly("Max Muster", "Anna Albers");
    }

    @Test
    void aContactCanBeChangedInPlace() throws Exception {
        Long id = locations.add(aLocation()).id();

        mvc.perform(post("/location/{id}/contact/{position}", id, 0)
                        .param("contactName", "Max Muster")
                        .param("contactEmail", "neu@example.org")
                        .param("contactPhone", "040 999"))
                .andExpect(status().isOk());

        assertThat(locations.byId(id).orElseThrow().contacts()).singleElement()
                .satisfies(contact -> {
                    assertThat(contact.email()).isEqualTo("neu@example.org");
                    assertThat(contact.phone()).isEqualTo("040 999");
                });
    }

    @Test
    void aContactCanBeRemovedAsLongAsOneIsLeft() throws Exception {
        Long id = locations.add(aLocation()
                .withAdditionalContact(ContactPerson.of("Anna Albers", "anna@example.org"))).id();

        mvc.perform(post("/location/{id}/contact/{position}/remove", id, 0))
                .andExpect(status().isOk());

        assertThat(locations.byId(id).orElseThrow().contacts())
                .extracting(ContactPerson::name).containsExactly("Anna Albers");
    }

    @Test
    void theLastContactIsRefusedWithAReasonAndNothingChanges() throws Exception {
        Long id = locations.add(aLocation()).id();

        String fragment = mvc.perform(post("/location/{id}/contact/{position}/remove", id, 0))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("letzte Ansprechpartner");
        assertThat(locations.byId(id).orElseThrow().contacts()).hasSize(1);
    }

    @Test
    void aContactWithoutAnAddressIsRefused() throws Exception {
        Long id = locations.add(aLocation()).id();

        String fragment = mvc.perform(post("/location/{id}/contact", id)
                        .param("contactName", "Anna Albers")
                        .param("contactEmail", "")
                        .param("contactPhone", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("E-Mail-Adresse");
        assertThat(locations.byId(id).orElseThrow().contacts()).hasSize(1);
    }

    @Test
    void theDetailPageOffersOneFormPerContactAndFoldsAwayTheOneToAdd() throws Exception {
        Long id = locations.add(aLocation()
                .withAdditionalContact(ContactPerson.of("Anna Albers", "anna@example.org"))).id();

        String html = mvc.perform(get("/location/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#contact-list form")).hasSize(2);

        // The form for a further contact sits beside the list, folded away.
        Element reveal = page.select("details.reveal").last();
        assertThat(reveal.hasAttr("open")).isFalse();
        assertThat(reveal.selectFirst("summary").text()).isEqualTo("Weiterer Ansprechpartner");
        assertThat(reveal.selectFirst("input[name=contactName]")).isNotNull();
        // The last form is the empty one to add with, so it carries no value at all.
        assertThat(page.select("#contact-list input[name=contactName]").eachAttr("value"))
                .containsExactly("Max Muster", "Anna Albers");
    }

    @Test
    void theSearchNarrowsTheList() throws Exception {
        locations.add(Location.of("Zeise Kinos", aContact()).withAddress(null, null, "Hamburg"));
        locations.add(Location.of("Adobe", aContact()).withAddress(null, null, "Lüneburg"));

        String html = mvc.perform(get("/location").param("search", "zeise"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#location-table tbody tr td:first-child").eachText())
                .containsExactly("Zeise Kinos");
    }

    @Test
    void anHtmxRequestGetsTheBareTableAndNoPageAroundIt() throws Exception {
        locations.add(Location.of("Zeise Kinos", aContact()));

        String fragment = mvc.perform(get("/location").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        Document parsed = Jsoup.parseBodyFragment(fragment);
        assertThat(parsed.selectFirst("div#location-table table")).isNotNull();
        assertThat(parsed.select("tbody tr td:first-child").eachText()).containsExactly("Zeise Kinos");
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String html = mvc.perform(get("/location")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#location-table caption").text())
                .isEqualTo("Keine Orte gefunden.");
    }

    // --- name and notes ---------------------------------------------------------------

    @Test
    void theNameAndTheNotesAreChangedOnTheDetailPage() throws Exception {
        Long id = locations.add(aLocation()).id();

        String fragment = mvc.perform(post("/location/" + id)
                        .param("name", "Nordsee GmbH")
                        .param("notes", "Parkplätze hinter dem Haus."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("input[name=name]").val()).isEqualTo("Nordsee GmbH");
        assertThat(tile.selectFirst("textarea[name=notes]").val())
                .isEqualTo("Parkplätze hinter dem Haus.");
        Location stored = locations.byId(id).orElseThrow();
        assertThat(stored.name()).isEqualTo("Nordsee GmbH");
        assertThat(stored.notes()).isEqualTo("Parkplätze hinter dem Haus.");
    }

    @Test
    void aLocationWithoutANameIsRefusedAndKeepsTheOldOne() throws Exception {
        Long id = locations.add(aLocation()).id();

        String fragment = mvc.perform(post("/location/" + id).param("name", "").param("notes", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Name");
        assertThat(locations.byId(id).orElseThrow().name()).isEqualTo("Musterfirma GmbH");
    }

    /** The form carries neither, so it must not be able to lose them. */
    @Test
    void renamingLeavesAddressesAndContactPeopleAlone() throws Exception {
        Long id = locations.add(aLocation().movedTo(Address.at("Musterweg 1", "22179", "Hamburg"))).id();

        mvc.perform(post("/location/" + id).param("name", "Nordsee GmbH").param("notes", ""))
                .andExpect(status().isOk());

        Location stored = locations.byId(id).orElseThrow();
        assertThat(stored.addressLine()).contains("Musterweg 1");
        assertThat(stored.contacts()).extracting(ContactPerson::name).containsExactly("Max Muster");
    }
}
