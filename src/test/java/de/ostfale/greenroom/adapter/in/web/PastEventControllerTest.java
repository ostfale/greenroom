package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.events.EventStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static de.ostfale.greenroom.Fixtures.aLocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing down what already happened. One form, one row — the state machine is for planning
 * and would be four clicks of ceremony per evening here.
 */
@WebTest
class PastEventControllerTest {

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

    private Long place;

    @BeforeEach
    void aPlaceToHaveBeenAt() {
        database.empty();
        place = locations.add(aLocation()).id();
    }

    @Test
    void anEveningThatHappenedIsWrittenDownInOneGo() throws Exception {
        mvc.perform(anEvening(Map.of()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(evening -> {
            assertThat(evening.status()).isEqualTo(EventStatus.DONE);
            assertThat(evening.date()).isEqualTo("2019-11-14");
            assertThat(evening.locationId()).isEqualTo(place);
            assertThat(evening.mode()).isEqualTo(EventMode.ONSITE);
            assertThat(evening.talkAt(0).title()).isEqualTo("Records in Java 25");
            assertThat(evening.talkAt(0).speakers().getFirst().announcedBio())
                    .isEqualTo("Damals bei der Musterfirma");
        });
    }

    @Test
    void theHourItBeganAtIsWrittenDownWithIt() throws Exception {
        mvc.perform(anEvening(Map.of("startsAt", "18:30")))
                .andExpect(status().is3xxRedirection());

        assertThat(events.all()).singleElement().satisfies(evening ->
                assertThat(evening.talkAt(0).startsAt()).isEqualTo(LocalTime.of(18, 30)));
    }

    /** Ten years ago nobody wrote the hour down, and the form does not invent one. */
    @Test
    void anEveningWithoutAnHourKeepsNone() throws Exception {
        mvc.perform(anEvening(Map.of("startsAt", "")))
                .andExpect(status().is3xxRedirection());

        assertThat(events.all()).singleElement()
                .satisfies(evening -> assertThat(evening.startsAt()).isNull());
    }

    /** The one thing the chain would have given, and the record gives it anyway. */
    @Test
    void anEveningThatIsOverStillNeedsWhatBeingOverPromises() throws Exception {
        String html = mvc.perform(anEvening(Map.of("abstractText", "")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Titel und eine Beschreibung");
        assertThat(page.selectFirst("input[name=speakerName]").val()).isEqualTo("Max Muster");
        assertThat(events.all()).isEmpty();
        assertThat(speakers.all()).isEmpty();
    }

    @Test
    void anEveningThatWasCalledOffNeedsNoneOfIt() throws Exception {
        mvc.perform(anEvening(Map.of("status", "CANCELLED", "abstractText", "", "locationId", "")))
                .andExpect(status().is3xxRedirection());

        assertThat(events.all()).singleElement()
                .extracting(Event::status).isEqualTo(EventStatus.CANCELLED);
    }

    /** Only an ending is an ending: a plan is not something that already happened. */
    @Test
    void aStatusThatIsNotAnEndingIsRefused() throws Exception {
        String html = mvc.perform(anEvening(Map.of("status", "DRAFT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text())
                .contains("stattgefunden oder wurde abgesagt");
        assertThat(events.all()).isEmpty();
    }

    @Test
    void theFormOffersOnlyTheTwoWaysAnEveningEnds() throws Exception {
        String html = mvc.perform(get("/event/past"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("select[name=status] option").eachText())
                .containsExactly("Erledigt", "Abgesagt");
    }

    /** The address is the person: whoever spoke before is not written down a second time. */
    @Test
    void aSpeakerWhoAlreadySpokeIsFoundByTheirAddress() throws Exception {
        mvc.perform(anEvening(Map.of("date", "2019-11-14")));
        mvc.perform(anEvening(Map.of("date", "2020-05-07", "speakerName", "Wer auch immer")));

        assertThat(events.all()).hasSize(2);
        assertThat(speakers.all()).singleElement()
                .satisfies(person -> assertThat(person.name()).isEqualTo("Max Muster"));
    }

    @Test
    void aPastEveningNeedsItsDate() throws Exception {
        String html = mvc.perform(anEvening(Map.of("date", "")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("Datum");
        assertThat(events.all()).isEmpty();
    }

    @Test
    void theListLinksToTheForm() throws Exception {
        String html = mvc.perform(get("/event"))
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("div.actions a").eachAttr("href"))
                .contains("/event/past");
    }

    /** Everything filled in, unless the test says otherwise. */
    private MockHttpServletRequestBuilder anEvening(Map<String, String> changed) {
        Map<String, String> fields = new HashMap<>(Map.of(
                "date", "2019-11-14",
                "startsAt", "19:00",
                "mode", "ONSITE",
                "status", "DONE",
                "speakerName", "Max Muster",
                "speakerEmail", "max@example.org",
                "title", "Records in Java 25",
                "abstractText", "Was Records sind und wofür sie taugen.",
                "announcedBio", "Damals bei der Musterfirma",
                "locationId", String.valueOf(place)));
        fields.putAll(changed);
        MockHttpServletRequestBuilder request = post("/event/past");
        fields.forEach(request::param);
        return request;
    }
}
