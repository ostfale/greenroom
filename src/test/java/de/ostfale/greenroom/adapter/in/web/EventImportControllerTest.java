package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.ostfale.greenroom.Fixtures.aLocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Entering the last ten years: one form, one finished evening, no planning history. */
@WebTest
class EventImportControllerTest {

    private static final LocalDate BACK_THEN = LocalDate.of(2017, 3, 14);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageEvents events;

    @Autowired
    private ManageSpeakers speakers;

    @Autowired
    private ManageLocations locations;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheDatabase() {
        database.empty();
    }

    /**
     * A complete past evening. The map replaces single fields — MockMvc.param adds a
     * second value rather than overwriting the first, which would quietly test nothing.
     */
    private static MockHttpServletRequestBuilder anEvening(Map<String, String> instead) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("date", "2017-03-14");
        values.put("mode", "ONSITE");
        values.put("speakerName", "Max Muster");
        values.put("speakerEmail", "max@example.org");
        values.put("title", "Records in Java 25");
        values.put("abstractText", "Warum Records mehr sind als weniger Tippen.");
        values.put("announcedBio", "Schrieb 2017 vor allem XML.");
        values.put("locationId", "");
        values.putAll(instead);

        MockHttpServletRequestBuilder request = post("/event/import");
        values.forEach(request::param);
        return request;
    }

    @Test
    void aPastEveningWithAVenueIsDone() throws Exception {
        Long locationId = locations.add(aLocation()).id();

        mvc.perform(anEvening(Map.of("locationId", locationId.toString())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(EventStatus.DONE);
            assertThat(stored.date()).isEqualTo(BACK_THEN);
            assertThat(stored.locationId()).isEqualTo(locationId);
            assertThat(stored.displayName()).isEqualTo("Records in Java 25");
        });
    }

    /** Without a place the evening stops where an evening without a place stops. */
    @Test
    void withoutAVenueItGetsAsFarAsTheDate() throws Exception {
        mvc.perform(anEvening(Map.of())).andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(EventStatus.DATE_CONFIRMED);
            assertThat(stored.locationId()).isNull();
        });
    }

    /** PUBLISHED wants a title and an abstract; a half-remembered evening keeps the venue. */
    @Test
    void withoutAnAbstractItStopsAtTheVenue() throws Exception {
        Long locationId = locations.add(aLocation()).id();

        mvc.perform(anEvening(Map.of("abstractText", "", "locationId", locationId.toString())))
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored ->
                assertThat(stored.status()).isEqualTo(EventStatus.VENUE_CONFIRMED));
    }

    @Test
    void theSpeakerIsCreatedWithNothingButNameAndAddress() throws Exception {
        mvc.perform(anEvening(Map.of())).andExpect(redirectedUrl("/event"));

        assertThat(speakers.all()).singleElement().satisfies(stored -> {
            assertThat(stored.name()).isEqualTo("Max Muster");
            assertThat(stored.email()).isEqualTo("max@example.org");
            assertThat(stored.bio()).isNull();
            assertThat(stored.company()).isNull();
        });
    }

    /** The biography of 2017 belongs to that evening, not to the person of today. */
    @Test
    void theBiographyGoesOntoTheTalkAndNotOntoTheSpeaker() throws Exception {
        mvc.perform(anEvening(Map.of())).andExpect(redirectedUrl("/event"));

        Event stored = events.all().getFirst();
        assertThat(stored.talkAt(0).speakers()).singleElement().satisfies(announced ->
                assertThat(announced.announcedBio()).isEqualTo("Schrieb 2017 vor allem XML."));
        assertThat(speakers.all().getFirst().bio()).isNull();
    }

    @Test
    void somebodyWhoSpokeBeforeIsRecognisedByTheirAddress() throws Exception {
        Long known = speakers.add(Speaker.of("Max Muster", "max@example.org")
                .withBio("Schreibt heute Java.")).id();

        mvc.perform(anEvening(Map.of())).andExpect(redirectedUrl("/event"));
        mvc.perform(anEvening(Map.of("date", "2019-05-08")))
                .andExpect(redirectedUrl("/event"));

        assertThat(speakers.all()).singleElement()
                .satisfies(stored -> assertThat(stored.id()).isEqualTo(known));
        assertThat(events.all()).hasSize(2);
        assertThat(speakers.byId(known).orElseThrow().bio()).isEqualTo("Schreibt heute Java.");
    }

    @Test
    void theAddressIsMatchedWhateverTheCase() throws Exception {
        Long known = speakers.add(Speaker.of("Max Muster", "Max@Example.org")).id();

        mvc.perform(anEvening(Map.of())).andExpect(redirectedUrl("/event"));

        assertThat(speakers.all()).singleElement()
                .satisfies(stored -> assertThat(stored.id()).isEqualTo(known));
    }

    @Test
    void theModeIsKeptForThePandemicYears() throws Exception {
        mvc.perform(anEvening(Map.of("mode", "ONLINE")))
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all().getFirst().mode()).isEqualTo(EventMode.ONLINE);
    }

    @Test
    void aPastEveningNeedsItsDate() throws Exception {
        String html = mvc.perform(anEvening(Map.of("date", "")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Datum");
        assertThat(page.selectFirst("input[name=speakerName]").val()).isEqualTo("Max Muster");
        assertThat(events.all()).isEmpty();
        assertThat(speakers.all()).isEmpty();
    }

    @Test
    void aPastEveningNeedsTheAddressOfItsSpeaker() throws Exception {
        String html = mvc.perform(anEvening(Map.of("speakerEmail", "")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("E-Mail-Adresse");
        assertThat(events.all()).isEmpty();
        assertThat(speakers.all()).isEmpty();
    }

    @Test
    void theFormOffersTheKnownPlacesAndTheThreeForms() throws Exception {
        locations.add(aLocation());

        String html = mvc.perform(get("/event/import")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("select[name=locationId] option").eachText())
                .containsExactly("Nicht erfasst", "Musterfirma GmbH");
        assertThat(page.select("select[name=mode] option").eachText())
                .containsExactly("Vor Ort", "Online", "Hybrid");
    }

    @Test
    void theListLeadsToTheImport() throws Exception {
        String html = mvc.perform(get("/event")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select(".headline a").eachAttr("href"))
                .contains("/event/import");
    }

    /** The route must not be read as an event with the id "import". */
    @Test
    void theImportRouteWinsOverTheDetailRoute() throws Exception {
        events.add(Event.draftFor(Talk.by(TalkSpeaker.of(
                speakers.add(Speaker.of("Max Muster", "max@example.org")).id()))));

        String html = mvc.perform(get("/event/import")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("h1").text()).isEqualTo("Vergangenes Event");
    }
}
