package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.domain.event.Event;
import de.ostfale.greenroom.domain.event.EventStatus;
import de.ostfale.greenroom.domain.event.Talk;
import de.ostfale.greenroom.domain.event.TalkSpeaker;
import de.ostfale.greenroom.domain.location.ContactPerson;
import de.ostfale.greenroom.domain.location.Location;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

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
class EventControllerTest {

    private static final LocalDate EVENING = LocalDate.of(2026, 9, 24);

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

    @Autowired
    private LocationRepository locationRepository;

    private Long speakerId;

    @BeforeEach
    void aSpeakerToPointAt() {
        eventRepository.deleteAll();
        speakerRepository.deleteAll();
        locationRepository.deleteAll();
        speakerId = speakers.add(Speaker.of("Max Muster", "max@example.org")).id();
    }

    /**
     * @SpringBootTest commits for real, so the events have to go before another test tries
     * to empty the speaker table — a speaker who gave a talk cannot be deleted.
     */
    @AfterEach
    void leaveNoEventsBehind() {
        eventRepository.deleteAll();
    }

    private Talk readyTalk() {
        return Talk.by(TalkSpeaker.of(speakerId))
                .withTitle("Records in Java 25")
                .withAbstract("Warum Records mehr sind als weniger Tippen.");
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
        events.add(Event.draftFor(readyTalk())
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
        events.add(Event.draftFor(readyTalk()).withMotto("Noch offen"));
        events.add(Event.draftFor(readyTalk()).withMotto("Abgesagt").moveTo(EventStatus.CANCELLED));

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
        events.add(Event.draftFor(readyTalk()));

        String fragment = mvc.perform(get("/event").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment.strip()).startsWith("<table").doesNotContain("<html").doesNotContain("<header");
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("table#event-table")).isNotNull();
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
        events.add(Event.draftFor(readyTalk()).withMotto("Ohne Termin"));
        events.add(Event.draftFor(readyTalk()).withMotto("Alt").withDate(EVENING.minusMonths(1)));
        events.add(Event.draftFor(readyTalk()).withMotto("Neu").withDate(EVENING));

        String html = mvc.perform(get("/event")).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("#event-table tbody tr td:nth-child(2)").eachText())
                .containsExactly("Neu", "Alt", "Ohne Termin");
    }
}
