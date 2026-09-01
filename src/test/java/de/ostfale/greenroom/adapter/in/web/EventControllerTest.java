package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aLocation;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static de.ostfale.greenroom.Fixtures.anAddress;
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
class EventControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageEvents events;

    @Autowired
    private ManageSpeakers speakers;

    @Autowired
    private ManageLocations locations;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerRepository speakerRepository;

    private Long speakerId;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void aSpeakerToPointAt() {
        database.empty();
        speakerId = speakers.add(aSpeaker()).id();
    }

    @Test
    void theFormPostsANewTopicWithItsTalkAndSpeaker() throws Exception {
        mvc.perform(post("/event")
                        .param("speakerId", speakerId.toString())
                        .param("title", "Records in Java 25")
                        .param("date", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(EventStatus.DRAFT);
            assertThat(stored.date()).isNull();
            assertThat(stored.displayName()).isEqualTo("Records in Java 25");
            assertThat(stored.talks()).singleElement().satisfies(talk ->
                    assertThat(talk.speakers()).extracting(TalkSpeaker::speakerId)
                            .containsExactly(speakerId));
        });
    }

    @Test
    void aTopicMayAlreadyCarryADateAndStaysADraft() throws Exception {
        mvc.perform(post("/event")
                        .param("speakerId", speakerId.toString())
                        .param("title", "")
                        .param("date", "2026-09-24"))
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored -> {
            assertThat(stored.date()).isEqualTo(EVENING);
            assertThat(stored.status()).isEqualTo(EventStatus.DRAFT);
            assertThat(stored.displayName()).isNull();
        });
    }

    @Test
    void withoutASpeakerTheFormComesBackWithWhatWasTyped() throws Exception {
        String html = mvc.perform(post("/event")
                        .param("speakerId", "")
                        .param("title", "Records in Java 25")
                        .param("date", "2026-09-24"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("Referenten");
        assertThat(page.selectFirst("input[name=title]").val()).isEqualTo("Records in Java 25");
        assertThat(page.selectFirst("input[name=date]").val()).isEqualTo("2026-09-24");
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void aDateThatIsNoDateSaysSoInsteadOfFailing() throws Exception {
        String html = mvc.perform(post("/event")
                        .param("speakerId", speakerId.toString())
                        .param("title", "")
                        .param("date", "irgendwann"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("p.error").text()).contains("Datum");
        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void theFormOffersTheKnownSpeakersAndNothingElse() throws Exception {
        speakers.add(Speaker.of("Anna Albers", "anna@example.org").withContact("Nordsee GmbH", "anna@example.org", null));

        String html = mvc.perform(get("/event/new")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("select[name=speakerId] option").eachText())
                .containsExactly("Bitte auswählen", "Anna Albers – Nordsee GmbH", "Max Muster");
    }

    @Test
    void withoutASpeakerInTheSystemTheFormSendsYouToCreateOne() throws Exception {
        speakerRepository.deleteAll();

        String html = mvc.perform(get("/event/new")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.selectFirst("p.error").text()).contains("kein Referent");
        assertThat(page.select("form")).isEmpty();
        assertThat(page.selectFirst("a.button").attr("href")).isEqualTo("/speaker/new");
    }

    @Test
    void theListShowsDateNameStatusAndVenue() throws Exception {
        Long locationId = locations.add(Location.of("Musterfirma GmbH",
                ContactPerson.of("Anna Albers", "anna@example.org"))).id();
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withMotto("Java-Herbst")
                .withLocation(locationId)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED));

        String html = mvc.perform(get("/event")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#event-table tbody tr td").eachText())
                .containsExactly("24.09.2026", "Java-Herbst", "Ort steht", "Musterfirma GmbH", "1");
    }

    @Test
    void aTopicShowsADashForWhatIsNotSettledYet() throws Exception {
        events.add(Event.draftFor(Talk.by(TalkSpeaker.of(speakerId))));

        String html = mvc.perform(get("/event")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-table tbody tr td").eachText())
                .containsExactly("—", "Ohne Titel", "Thema", "—", "1");
    }

    @Test
    void theFilterHidesWhatIsOverAndDoneWith() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Noch offen"));
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Abgesagt").moveTo(EventStatus.CANCELLED));

        String all = mvc.perform(get("/event")).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(all).select("#event-table tbody tr td:nth-child(2)").eachText())
                .containsExactlyInAnyOrder("Noch offen", "Abgesagt");

        String open = mvc.perform(get("/event").param("hideClosed", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(open).select("#event-table tbody tr td:nth-child(2)").eachText())
                .containsExactly("Noch offen");
    }

    @Test
    void anHtmxRequestGetsTheBareTableAndNoPageAroundIt() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId)));

        String fragment = mvc.perform(get("/event").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<div").doesNotContain("<html").doesNotContain("<header");
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("div#event-table table")).isNotNull();
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingAnEmptyTable() throws Exception {
        String html = mvc.perform(get("/event")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#event-table caption").text())
                .isEqualTo("Noch kein Event geplant.");
    }

    @Test
    void theFrontPageIsTheListOfEvenings() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/event"));
    }

    @Test
    void theListIsNewestFirstWithTheUndatedTopicsLast() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Ohne Termin"));
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Alt").withDate(EVENING.minusMonths(1)));
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Neu").withDate(EVENING));

        String html = mvc.perform(get("/event")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-table tbody tr td:nth-child(2)").eachText())
                .containsExactly("Neu", "Alt", "Ohne Termin");
    }

    // --- the evening itself ---------------------------------------------------------

    @Test
    void theListLinksToTheEvening() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Java-Herbst")).id();

        String html = mvc.perform(get("/event")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).selectFirst("#event-table tbody tr a").attr("href"))
                .isEqualTo("/event/" + id);
    }

    @Test
    void theDetailPageShowsTheEveningWithItsTalkAndSpeaker() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withMotto("Java-Herbst")).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("h1").text()).isEqualTo("Java-Herbst");
        assertThat(page.selectFirst("input[name=date]").val()).isEqualTo("2026-09-24");
        assertThat(page.selectFirst("input[name=motto]").val()).isEqualTo("Java-Herbst");
        assertThat(page.selectFirst("#event-status .label").text()).isEqualTo("Thema");
        assertThat(page.select(".tile ul.plain li strong").eachText())
                .containsExactly("Records in Java 25");
        assertThat(page.selectFirst(".tile ul.plain li p.hint").text()).isEqualTo("Max Muster");
    }

    @Test
    void anEveningWithoutAVenueSaysSoInsteadOfShowingNothing() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select(".tile p.hint").eachText())
                .anyMatch(text -> text.contains("Noch kein Ort zugeordnet"));
    }

    @Test
    void theDetailPageNamesTheVenueOnceThereIsOne() throws Exception {
        Long locationId = locations.add(aLocation().movedTo(anAddress())).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withLocation(locationId)).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).text()).contains("Musterfirma GmbH", "Musterweg 1");
    }

    @Test
    void anUnknownEveningGoesBackToTheList() throws Exception {
        mvc.perform(get("/event/404"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/event"));
    }

    // --- date and motto -------------------------------------------------------------

    @Test
    void theDateAndTheMottoAreChangedOnTheDetailPage() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id)
                        .param("date", "2026-09-24")
                        .param("motto", "Java-Herbst"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("input[name=motto]").val())
                .isEqualTo("Java-Herbst");
        Event stored = events.byId(id).orElseThrow();
        assertThat(stored.date()).isEqualTo(EVENING);
        assertThat(stored.motto()).isEqualTo("Java-Herbst");
    }

    @Test
    void theDateOfASettledEveningCannotSimplyBeCleared() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)).id();

        String fragment = mvc.perform(post("/event/" + id).param("date", "").param("motto", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Datum");
        assertThat(events.byId(id).orElseThrow().date()).isEqualTo(EVENING);
    }

    // --- the state machine, from the outside -----------------------------------------

    @Test
    void theStatusTileOffersExactlyTheStepsTheMachineAllows() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-status .actions button").eachText())
                .containsExactly("Ort steht", "Verschoben", "Abgesagt");
    }

    @Test
    void aClosedEveningOffersNoStepAtAll() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .moveTo(EventStatus.CANCELLED)).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#event-status .actions button")).isEmpty();
        assertThat(page.selectFirst("#event-status p.hint").text()).contains("kein Schritt");
    }

    @Test
    void confirmingTheDateMovesTheEveningOn() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();

        String fragment = mvc.perform(post("/event/" + id + "/status")
                        .param("target", "DATE_CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("#event-status .label").text())
                .isEqualTo("Termin steht");
        assertThat(events.byId(id).orElseThrow().status()).isEqualTo(EventStatus.DATE_CONFIRMED);
    }

    @Test
    void aStepTheEveningIsNotReadyForNamesWhatIsMissing() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/status")
                        .param("target", "DATE_CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("p.error").text()).contains("Datum");
        assertThat(tile.selectFirst("#event-status .label").text()).isEqualTo("Thema");
        assertThat(events.byId(id).orElseThrow().status()).isEqualTo(EventStatus.DRAFT);
    }

    /** Until a venue can be picked, this is as far as an evening gets — and it says so. */
    @Test
    void confirmingTheVenueAsksForOne() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .moveTo(EventStatus.DATE_CONFIRMED)).id();

        String fragment = mvc.perform(post("/event/" + id + "/status")
                        .param("target", "VENUE_CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Ort");
        assertThat(events.byId(id).orElseThrow().status()).isEqualTo(EventStatus.DATE_CONFIRMED);
    }

    /**
     * A page left open overnight can post a step the evening has long moved past. The
     * status is read from the database, never from the request, so it is simply refused.
     */
    @Test
    void aStepTheMachineForbidsIsRefusedEvenWhenItIsPosted() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/status").param("target", "DONE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("nicht möglich");
        assertThat(events.byId(id).orElseThrow().status()).isEqualTo(EventStatus.DRAFT);
    }
}
