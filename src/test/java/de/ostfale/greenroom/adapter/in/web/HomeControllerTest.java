package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.WebTest;
import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageNotes;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.tags.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.aLocation;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static de.ostfale.greenroom.Fixtures.aTalk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The page the tool opens with: what is next, what is missing, and how much there is. */
@WebTest
class HomeControllerTest {

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
    private ManageNotes notes;

    @Autowired
    private TestDatabase database;

    private Long speakerId;
    private Long place;

    @BeforeEach
    void aSpeakerAndAPlace() {
        database.empty();
        speakerId = speakers.add(aSpeaker()).id();
        place = locations.add(aLocation()).id();
    }

    @Test
    void theFrontPageIsTheOverview() throws Exception {
        Document page = overview();

        assertThat(page.selectFirst("h1").text()).isEqualTo("Übersicht");
        assertThat(page.select("section.tile h2").eachText())
                .containsExactly("Der nächste Abend", "Weiter geplant", "Themen ohne Termin",
                        "Wo wir schon waren", "Wen wir schon hatten", "Zahlen");
    }

    @Test
    void theNearestEveningStillBeingPlannedIsTheOneOnTop() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Später").withDate(LocalDate.now().plusMonths(3)));
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Bald").withDate(LocalDate.now().plusDays(12)));

        Document page = overview();

        assertThat(page.selectFirst("section.tile a").text()).isEqualTo("Bald");
        assertThat(page.selectFirst("section.tile p.data").text()).contains("in 12 Tagen");
    }

    /** An evening that is over is not in planning any more, however it went. */
    @Test
    void aClosedEveningIsNotWhatIsNext() throws Exception {
        events.add(done(LocalDate.now().plusDays(2)));

        Document page = overview();

        assertThat(page.selectFirst("section.tile p.hint").text())
                .isEqualTo("Kein Abend mit Termin in Planung.");
    }

    @Test
    void everyOpenEveningSaysWhatItWaitsFor() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId))
                .withMotto("Ohne Ort").withDate(LocalDate.now().plusDays(5)));
        events.add(Event.draftFor(aTalk(speakerId))
                .withMotto("Ohne Abstract").withDate(LocalDate.now().plusDays(20))
                .withLocation(place));

        Document page = overview();

        assertThat(page.selectFirst("section.tile p.hint").text()).contains("Ort fehlt");
        assertThat(page.select("section.tile table td.hint").eachText()).contains("Abstract fehlt");
    }

    @Test
    void aTopicWithoutADateIsWaitingForASlot() throws Exception {
        events.add(Event.draftFor(aReadyTalk(speakerId)).withMotto("Noch ohne Termin"));

        Document page = overview();

        assertThat(page.select("ul.plain a").eachText()).containsExactly("Noch ohne Termin");
    }

    @Test
    void thePlacesAndThePeopleAreCountedByHowOftenTheyCame() throws Exception {
        events.add(done(LocalDate.of(2024, 3, 14)));
        events.add(done(LocalDate.of(2025, 9, 11)));

        Document page = overview();

        assertThat(page.select("section.tile").get(3).select("tbody tr td").eachText())
                .containsSequence("Musterfirma GmbH", "2", "2025");
        assertThat(page.select("section.tile").get(4).select("tbody tr td").eachText())
                .containsSequence("Max Muster", "2", "2025");
    }

    @Test
    void theNumbersAreCountedFromWhatIsThere() throws Exception {
        events.add(done(LocalDate.now()));
        events.add(done(LocalDate.now().minusYears(1)));
        events.add(Event.draftFor(aReadyTalk(speakerId)));
        tags.add(Tag.named("Java"));
        notes.add("Beamer", "steht im Schrank");

        assertThat(overview().select("ul.tally").text())
                .contains("3 Events")
                .contains("1 dieses Jahr")
                .contains("2 erledigt")
                .contains("1 Referenten")
                .contains("1 von 1 Orten aktiv")
                .contains("1 Tags")
                .contains("1 Notizen");
    }

    private Event done(LocalDate on) {
        return Event.draftFor(aReadyTalk(speakerId))
                .withDate(on)
                .withLocation(place)
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED)
                .moveTo(EventStatus.PUBLISHED)
                .moveTo(EventStatus.DONE);
    }

    private Document overview() throws Exception {
        return Jsoup.parse(mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
