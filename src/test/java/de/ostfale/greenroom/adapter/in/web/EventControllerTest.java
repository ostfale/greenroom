package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakerInquiries;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.activities.ContactChannel;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.tags.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aContact;
import static de.ostfale.greenroom.Fixtures.aLocation;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static de.ostfale.greenroom.Fixtures.aTalk;
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
    private ManageTags tags;

    @Autowired
    private ManageSpeakerInquiries inquiries;

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
        assertThat(page.selectFirst("#event-talks input[name=title]").val())
                .isEqualTo("Records in Java 25");
        assertThat(page.selectFirst("#event-talks label span").text()).isEqualTo("Kurzvita Max Muster");
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

    // --- the venue ------------------------------------------------------------------

    @Test
    void theVenueIsPickedFromTheKnownLocations() throws Exception {
        locations.add(aLocation());
        locations.add(Location.of("Nordsee GmbH", aContact()));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-venue select[name=locationId] option").eachText())
                .containsExactly("Noch offen", "Musterfirma GmbH", "Nordsee GmbH");
    }

    @Test
    void withoutAnyLocationTheTileSendsYouToCreateOne() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#event-venue form")).isEmpty();
        assertThat(page.selectFirst("#event-venue a.button").attr("href")).isEqualTo("/location/new");
    }

    @Test
    void assigningAVenueStoresItAndNamesItOnTheTile() throws Exception {
        Long locationId = locations.add(aLocation().movedTo(anAddress())).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/location")
                        .param("locationId", locationId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.text()).contains("Musterfirma GmbH", "Musterweg 1");
        assertThat(tile.selectFirst("#event-venue option[selected]").attr("value"))
                .isEqualTo(locationId.toString());
        assertThat(events.byId(id).orElseThrow().locationId()).isEqualTo(locationId);
    }

    @Test
    void aDraftMayLoseItsVenueAgain() throws Exception {
        Long locationId = locations.add(aLocation()).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withLocation(locationId)).id();

        String fragment = mvc.perform(post("/event/" + id + "/location").param("locationId", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("#event-venue p.hint").text())
                .contains("Noch kein Ort zugeordnet");
        assertThat(events.byId(id).orElseThrow().locationId()).isNull();
    }

    @Test
    void aConfirmedVenueCannotSimplyBeTakenAway() throws Exception {
        Long locationId = locations.add(aLocation()).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withLocation(locationId)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED)).id();

        String fragment = mvc.perform(post("/event/" + id + "/location").param("locationId", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text()).contains("Ort");
        assertThat(events.byId(id).orElseThrow().locationId()).isEqualTo(locationId);
    }

    /**
     * The whole way, in the order the planning takes it: a topic gets a date, a host and
     * an announcement. Until the venue could be picked this stopped after the date.
     */
    @Test
    void anEveningGoesFromTopicToAnnouncedThroughThePage() throws Exception {
        Long locationId = locations.add(aLocation().movedTo(anAddress())).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        mvc.perform(post("/event/" + id).param("date", "2026-09-24").param("motto", ""))
                .andExpect(status().isOk());
        mvc.perform(post("/event/" + id + "/status").param("target", "DATE_CONFIRMED"))
                .andExpect(status().isOk());
        mvc.perform(post("/event/" + id + "/location").param("locationId", locationId.toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/event/" + id + "/status").param("target", "VENUE_CONFIRMED"))
                .andExpect(status().isOk());
        String fragment = mvc.perform(post("/event/" + id + "/status").param("target", "PUBLISHED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("p.error")).isEmpty();
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("#event-status .label").text())
                .isEqualTo("Veröffentlicht");
        assertThat(events.byId(id).orElseThrow().status()).isEqualTo(EventStatus.PUBLISHED);
    }

    // --- two evenings on one day ------------------------------------------------------

    @Test
    void anotherEveningOnTheSameDayIsNamedAsAWarning() throws Exception {
        Long other = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Java-Herbst")
                .withDate(EVENING)).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#event-basics p.notice").text())
                .contains("An diesem Tag ist bereits geplant", "Java-Herbst");
        assertThat(page.selectFirst("#event-basics p.notice a").attr("href"))
                .isEqualTo("/event/" + other);
    }

    @Test
    void anEveningDoesNotClashWithItself() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-basics p.notice")).isEmpty();
    }

    @Test
    void aTopicWithoutADateClashesWithNothing() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-basics p.notice")).isEmpty();
    }

    @Test
    void aCancelledEveningNoLongerOccupiesItsDay() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Abgesagt")
                .withDate(EVENING)
                .moveTo(EventStatus.CANCELLED));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();

        String html = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-basics p.notice")).isEmpty();
    }

    /** The warning is a warning: what was typed is stored, and then the page says so. */
    @Test
    void movingAnEveningOntoAnOccupiedDayStillStoresTheDate() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Java-Herbst")
                .withDate(EVENING));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id)
                        .param("date", "2026-09-24")
                        .param("motto", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.select("p.error")).isEmpty();
        assertThat(tile.selectFirst("p.notice").text()).contains("Java-Herbst");
        assertThat(events.byId(id).orElseThrow().date()).isEqualTo(EVENING);
    }

    // --- the talks of an evening -----------------------------------------------------

    @Test
    void everyTalkGetsItsOwnFormWithWhatIsStored() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withAdditionalTalk(aTalk(speakerOf("Anna Albers")))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#event-talks form.boxed:not(.new)")).hasSize(2);
        assertThat(page.select("#event-talks input[name=title]").eachAttr("value"))
                .containsExactly("Records in Java 25", "");
        assertThat(page.selectFirst("#event-talks textarea[name=abstractText]").val())
                .isEqualTo("Warum Records mehr sind als weniger Tippen.");
        assertThat(page.select("#event-talks .badge.quiet").eachText())
                .containsExactly("unvollständig");
    }

    @Test
    void theTitleAndTheAbstractOfATalkAreChanged() throws Exception {
        Long id = events.add(Event.draftFor(aTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk/0")
                        .param("title", "Records in Java 25")
                        .param("abstractText", "Warum Records mehr sind als weniger Tippen."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#event-talks .badge.quiet")).isEmpty();
        Talk stored = events.byId(id).orElseThrow().talkAt(0);
        assertThat(stored.title()).isEqualTo("Records in Java 25");
        assertThat(stored.abstractText()).isEqualTo("Warum Records mehr sind als weniger Tippen.");
    }

    /** The form carries no speakers, so it must not be able to lose them. */
    @Test
    void changingATalkLeavesItsSpeakerAlone() throws Exception {
        Long id = events.add(Event.draftFor(aTalk(speakerId))).id();

        mvc.perform(post("/event/" + id + "/talk/0")
                        .param("title", "Records in Java 25")
                        .param("abstractText", ""))
                .andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().talkAt(0).speakers())
                .extracting(TalkSpeaker::speakerId).containsExactly(speakerId);
    }

    @Test
    void aFurtherTalkComesWithItsOwnSpeaker() throws Exception {
        Long anna = speakerOf("Anna Albers");
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk")
                        .param("speakerId", anna.toString())
                        .param("title", "Virtual Threads"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#event-talks input[name=title]")
                .eachAttr("value")).containsExactly("Records in Java 25", "Virtual Threads");
        Event stored = events.byId(id).orElseThrow();
        assertThat(stored.talks()).hasSize(2);
        assertThat(stored.talkAt(1).speakers()).extracting(TalkSpeaker::speakerId)
                .containsExactly(anna);
    }

    @Test
    void aTalkWithoutASpeakerIsRefused() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk")
                        .param("speakerId", "")
                        .param("title", "Virtual Threads"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Referenten");
        assertThat(events.byId(id).orElseThrow().talks()).hasSize(1);
    }

    @Test
    void aTalkIsRemovedFromAnEveningThatCarriesTwo() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withAdditionalTalk(aTalk(speakerOf("Anna Albers")))).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk/0/remove"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#event-talks form.boxed:not(.new)"))
                .hasSize(1);
        assertThat(events.byId(id).orElseThrow().talks()).hasSize(1);
    }

    @Test
    void theLastTalkIsNeitherOfferedNorGivenUp() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String page = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(page).select("#event-talks button.danger")).isEmpty();

        String fragment = mvc.perform(post("/event/" + id + "/talk/0/remove"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("letzte Vortrag");
        assertThat(events.byId(id).orElseThrow().talks()).hasSize(1);
    }

    @Test
    void anAnnouncedEveningDoesNotLetGoOfATitle() throws Exception {
        Long locationId = locations.add(aLocation()).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withLocation(locationId)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .moveTo(EventStatus.PUBLISHED)).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk/0")
                        .param("title", "")
                        .param("abstractText", "Warum Records mehr sind als weniger Tippen."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Titel und eine Beschreibung");
        assertThat(events.byId(id).orElseThrow().talkAt(0).title()).isEqualTo("Records in Java 25");
    }

    @Test
    void anAnnouncedEveningTakesNoHalfFinishedTalk() throws Exception {
        Long locationId = locations.add(aLocation()).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withLocation(locationId)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .moveTo(EventStatus.PUBLISHED)).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk")
                        .param("speakerId", speakerOf("Anna Albers").toString())
                        .param("title", "Virtual Threads"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("Titel und eine Beschreibung");
        assertThat(events.byId(id).orElseThrow().talks()).hasSize(1);
    }

    private Long speakerOf(String name) {
        return speakers.add(Speaker.of(name, "anna@example.org")).id();
    }

    // --- who leads through the evening -----------------------------------------------

    @Test
    void theModeratorIsStoredWithTheOtherBasics() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id)
                        .param("date", "")
                        .param("motto", "")
                        .param("moderator", "Max Muster"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("input[name=moderator]").val())
                .isEqualTo("Max Muster");
        assertThat(events.byId(id).orElseThrow().moderator()).isEqualTo("Max Muster");
    }

    @Test
    void theModeratorStaysWhenTheEveningMovesOn() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withDate(EVENING)
                .withModerator("Max Muster")).id();

        mvc.perform(post("/event/" + id + "/status").param("target", "DATE_CONFIRMED"))
                .andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().moderator()).isEqualTo("Max Muster");
    }

    // --- the keywords of an evening ---------------------------------------------------

    @Test
    void theTagTileOffersTheMaintainedListAndTicksWhatIsCarried() throws Exception {
        tags.add(Tag.named("Spring"));
        tags.add(Tag.named("Architektur"));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withTags(List.of("Spring"))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        // The settings keep the list alphabetical, and the tile offers it in that order.
        assertThat(page.select("#event-tags ul.tags li").eachText())
                .containsExactly("Architektur", "Spring");
        assertThat(page.select("#event-tags input[checked]").eachAttr("value"))
                .containsExactly("Spring");
    }

    @Test
    void tickingAWordPutsItOnTheEvening() throws Exception {
        tags.add(Tag.named("Spring"));
        tags.add(Tag.named("Architektur"));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/tags")
                        .param("tag", "Spring")
                        .param("tag", "Architektur"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).select("#event-tags input[checked]")
                .eachAttr("value")).containsExactly("Architektur", "Spring");
        assertThat(events.byId(id).orElseThrow().tags()).containsExactly("Spring", "Architektur");
    }

    @Test
    void savingWithNothingTickedLeavesTheEveningWithoutWords() throws Exception {
        tags.add(Tag.named("Spring"));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withTags(List.of("Spring"))).id();

        mvc.perform(post("/event/" + id + "/tags")).andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().tags()).isEmpty();
    }

    /**
     * The evening stores the word, not a reference to the list. A tag that was dropped from
     * the settings is still offered here, ticked, so it does not fall off unnoticed.
     */
    @Test
    void aWordThatLeftTheSettingsStaysOnTheEveningItAnnounced() throws Exception {
        tags.add(Tag.named("Architektur"));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withTags(List.of("Spring"))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#event-tags ul.tags li").eachText())
                .containsExactly("Architektur", "Spring");
        assertThat(page.select("#event-tags input[checked]").eachAttr("value"))
                .containsExactly("Spring");
        assertThat(events.byId(id).orElseThrow().tags()).containsExactly("Spring");
    }

    @Test
    void withoutAnyKeywordTheTileSendsYouToTheSettings() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#event-tags form")).isEmpty();
        assertThat(page.selectFirst("#event-tags a.button").attr("href")).isEqualTo("/settings");
    }

    // --- the biography an evening announced -------------------------------------------

    @Test
    void theBiographyIsCopiedWhenTheSpeakerIsPutOnTheFirstTalk() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")
                .withBio("Schreibt seit 2009 Java.")).id();

        mvc.perform(post("/event")
                        .param("speakerId", anna.toString())
                        .param("title", "Virtual Threads")
                        .param("date", ""))
                .andExpect(redirectedUrl("/event"));

        assertThat(events.all()).singleElement().satisfies(stored ->
                assertThat(stored.talkAt(0).speakers()).singleElement().satisfies(announced ->
                        assertThat(announced.announcedBio()).isEqualTo("Schreibt seit 2009 Java.")));
    }

    @Test
    void theBiographyIsCopiedForAFurtherTalkToo() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")
                .withBio("Schreibt seit 2009 Java.")).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        mvc.perform(post("/event/" + id + "/talk")
                        .param("speakerId", anna.toString())
                        .param("title", "Virtual Threads"))
                .andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().talkAt(1).speakers()).singleElement()
                .satisfies(announced ->
                        assertThat(announced.announcedBio()).isEqualTo("Schreibt seit 2009 Java."));
    }

    @Test
    void theAnnouncedBiographyIsEditedOnTheTalk() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/talk/0")
                        .param("title", "Records in Java 25")
                        .param("abstractText", "Warum Records mehr sind als weniger Tippen.")
                        .param("announcedBio", "Hält seit Jahren Vorträge über Records."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment)
                .selectFirst("#event-talks textarea[name=announcedBio]").val())
                .isEqualTo("Hält seit Jahren Vorträge über Records.");
        assertThat(events.byId(id).orElseThrow().talkAt(0).speakers().getFirst().announcedBio())
                .isEqualTo("Hält seit Jahren Vorträge über Records.");
    }

    /**
     * The point of the copy: the person keeps rewriting their bio, but what stood on the
     * invitation for that evening stays where it is.
     */
    @Test
    void rewritingTheOwnBiographyLeavesTheAnnouncedOneAlone() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")
                .withBio("Schreibt seit 2009 Java.")).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        mvc.perform(post("/event/" + id + "/talk")
                .param("speakerId", anna.toString())
                .param("title", "Virtual Threads")).andExpect(status().isOk());

        mvc.perform(post("/speaker/" + anna)
                        .param("name", "Anna Albers")
                        .param("email", "anna@example.org")
                        .param("bio", "Arbeitet jetzt bei der Nordsee GmbH."))
                .andExpect(status().isOk());

        assertThat(speakers.byId(anna).orElseThrow().bio())
                .isEqualTo("Arbeitet jetzt bei der Nordsee GmbH.");
        assertThat(events.byId(id).orElseThrow().talkAt(1).speakers().getFirst().announcedBio())
                .isEqualTo("Schreibt seit 2009 Java.");
    }

    /** A stale page sends the wrong number of biographies; the stored ones are worth more. */
    @Test
    void aFormWithoutBiographiesLeavesThemWhereTheyAre() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")
                .withBio("Schreibt seit 2009 Java.")).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        mvc.perform(post("/event/" + id + "/talk")
                .param("speakerId", anna.toString())
                .param("title", "Virtual Threads")).andExpect(status().isOk());

        mvc.perform(post("/event/" + id + "/talk/1")
                        .param("title", "Virtual Threads")
                        .param("abstractText", "Was Loom wirklich ändert."))
                .andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().talkAt(1).speakers().getFirst().announcedBio())
                .isEqualTo("Schreibt seit 2009 Java.");
    }

    @Test
    void aSpeakerWithoutABiographyIsAnnouncedWithoutOne() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        mvc.perform(post("/event/" + id + "/talk")
                        .param("speakerId", speakerId.toString())
                        .param("title", "Virtual Threads"))
                .andExpect(status().isOk());

        assertThat(events.byId(id).orElseThrow().talkAt(1).speakers().getFirst().announcedBio())
                .isNull();
    }

    // --- asking the speaker -----------------------------------------------------------

    @Test
    void anEveningWithoutInquiriesSaysSo() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#event-inquiries p.hint").text()).contains("Noch niemand gefragt");
        assertThat(page.select("#event-inquiries table")).isEmpty();
    }

    @Test
    void theFormOffersOnlyThePeopleWhoSpeakAtThisEvening() throws Exception {
        speakers.add(Speaker.of("Wer Anders", "anders@example.org"));
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.select("#event-inquiries select[name=speakerId] option").eachText())
                .containsExactly("Bitte auswählen", "Max Muster");
    }

    @Test
    void anInquiryIsWrittenDownWithTheDateTheEveningHas() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();

        String fragment = mvc.perform(post("/event/" + id + "/inquiry")
                        .param("speakerId", speakerId.toString())
                        .param("channel", "EMAIL")
                        .param("sentAt", "2026-09-01")
                        .param("note", "Nachfassen am Montag."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.select("#event-inquiries tbody tr td").eachText())
                .contains("Max Muster", "01.09.2026", "E-Mail", "24.09.2026", "Nachfassen am Montag.");
        assertThat(inquiries.forEvent(id)).singleElement().satisfies(sent -> {
            assertThat(sent.askedAbout()).isEqualTo(EVENING);
            assertThat(sent.channel()).isEqualTo(ContactChannel.EMAIL);
            assertThat(sent.outcome()).isEqualTo(InquiryOutcome.PENDING);
        });
    }

    @Test
    void anInquiryWithoutAChannelIsRefused() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();

        String fragment = mvc.perform(post("/event/" + id + "/inquiry")
                        .param("speakerId", speakerId.toString())
                        .param("channel", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("auf welchem Weg");
        assertThat(inquiries.forEvent(id)).isEmpty();
    }

    @Test
    void anOpenInquiryShowsHowLongItHasBeenWaiting() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        LocalDate tenDaysAgo = LocalDate.now().minusDays(10);
        inquiries.send(SpeakerInquiry.sent(id, speakerId, null, tenDaysAgo, ContactChannel.EMAIL));

        Document page = Jsoup.parse(mvc.perform(get("/event/" + id))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#event-inquiries tbody .badge").text()).isEqualTo("Offen");
        assertThat(page.selectFirst("#event-inquiries tbody .hint").text())
                .isEqualTo("seit 10 Tagen");
    }

    @Test
    void theAnswerIsWrittenDownAndClosesTheInquiry() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        Long inquiryId = inquiries.send(SpeakerInquiry.sent(
                id, speakerId, EVENING, LocalDate.now(), ContactChannel.EMAIL)).id();

        String fragment = mvc.perform(post("/event/" + id + "/inquiry/" + inquiryId)
                        .param("outcome", "ACCEPTED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document tile = Jsoup.parseBodyFragment(fragment);
        assertThat(tile.selectFirst("#event-inquiries tbody .badge").text()).isEqualTo("Zugesagt");
        assertThat(tile.select("#event-inquiries tbody button")).isEmpty();
        assertThat(inquiries.forEvent(id).getFirst().isAccepted()).isTrue();
    }

    @Test
    void anAnsweredInquiryIsNotAnsweredAgain() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        Long inquiryId = inquiries.send(SpeakerInquiry.sent(
                id, speakerId, EVENING, LocalDate.now(), ContactChannel.EMAIL)).id();
        inquiries.answer(inquiryId, InquiryOutcome.DECLINED);

        String fragment = mvc.perform(post("/event/" + id + "/inquiry/" + inquiryId)
                        .param("outcome", "ACCEPTED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("schon beantwortet");
        assertThat(inquiries.forEvent(id).getFirst().outcome()).isEqualTo(InquiryOutcome.DECLINED);
    }

    /** A refusal is history, not a mistake: the second attempt stands next to the first. */
    @Test
    void askingAgainAfterARefusalKeepsBothAttempts() throws Exception {
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        Long first = inquiries.send(SpeakerInquiry.sent(
                id, speakerId, EVENING, LocalDate.now().minusDays(20), ContactChannel.EMAIL)).id();
        inquiries.answer(first, InquiryOutcome.DECLINED);

        mvc.perform(post("/event/" + id + "/inquiry")
                        .param("speakerId", speakerId.toString())
                        .param("channel", "PHONE"))
                .andExpect(status().isOk());

        assertThat(inquiries.forEvent(id)).hasSize(2);
        assertThat(inquiries.forEvent(id)).extracting(SpeakerInquiry::outcome)
                .containsExactly(InquiryOutcome.PENDING, InquiryOutcome.DECLINED);
    }

    @Test
    void onceEverybodyHasSaidYesTheTileSaysSo() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))
                .withAdditionalTalk(aTalk(anna))).id();
        Long forMax = inquiries.send(SpeakerInquiry.sent(
                id, speakerId, EVENING, LocalDate.now(), ContactChannel.EMAIL)).id();
        Long forAnna = inquiries.send(SpeakerInquiry.sent(
                id, anna, EVENING, LocalDate.now(), ContactChannel.EMAIL)).id();

        inquiries.answer(forMax, InquiryOutcome.ACCEPTED);
        String half = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(half).select("#event-inquiries p.notice")).isEmpty();

        inquiries.answer(forAnna, InquiryOutcome.ACCEPTED);
        String all = mvc.perform(get("/event/" + id)).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(all).selectFirst("#event-inquiries p.notice").text())
                .contains("Alle Referenten haben zugesagt");
    }

    @Test
    void aSpeakerThatWasAskedIsKeptEvenWithoutATalk() throws Exception {
        Long anna = speakers.add(Speaker.of("Anna Albers", "anna@example.org")).id();
        Long id = events.add(Event.draftFor(aReadyTalk(speakerId))).id();
        inquiries.send(SpeakerInquiry.sent(id, anna, EVENING, LocalDate.now(), ContactChannel.EMAIL));

        String fragment = mvc.perform(post("/speaker/" + anna + "/remove"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("p.error").text())
                .contains("angefragt");
        assertThat(speakers.byId(anna)).isPresent();
    }
}
