package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: the text an announcement is written from. */
class InvitationTextTest {

    private static final Long MAX = 1L;
    private static final Long ANNA = 2L;
    private static final Map<Long, String> NAMES = Map.of(MAX, "Max Muster", ANNA, "Anna Albers");

    @Test
    void theAbstractThenABlankLineThenWhoGivesIt() {
        Event evening = Event.draftFor(withBio(aReadyTalk(MAX), "Architektin bei der Musterfirma"));

        assertThat(InvitationText.of(evening, NAMES, "Referent")).isEqualTo("""
                Warum Records mehr sind als weniger Tippen.

                Referent - Max Muster
                Architektin bei der Musterfirma""");
    }

    /** Three talks are three blocks, and a blank line is what stands between them. */
    @Test
    void everyTalkGetsItsOwnBlock() {
        Event evening = Event.draftFor(withBio(aReadyTalk(MAX), "Von hier"))
                .withAdditionalTalk(withBio(aReadyTalk(ANNA), "Von dort")
                        .withAbstract("Was Streams damit zu tun haben."));

        assertThat(InvitationText.of(evening, NAMES, "Referent")).isEqualTo("""
                Warum Records mehr sind als weniger Tippen.

                Referent - Max Muster
                Von hier

                Was Streams damit zu tun haben.

                Referent - Anna Albers
                Von dort""");
    }

    @Test
    void twoVoicesOnOneTalkAreBothNamed() {
        Talk together = aReadyTalk(MAX)
                .withSpeakers(List.of(TalkSpeaker.of(MAX).withAnnouncedBio("Von hier"),
                        TalkSpeaker.of(ANNA).withAnnouncedBio("Von dort")));

        assertThat(InvitationText.of(Event.draftFor(together), NAMES, "Referent")).isEqualTo("""
                Warum Records mehr sind als weniger Tippen.

                Referent - Max Muster
                Von hier
                Referent - Anna Albers
                Von dort""");
    }

    /** What is not written down is left out rather than pasted in as a gap or a "null". */
    @Test
    void whatIsMissingIsSimplyNotThere() {
        Event withoutBio = Event.draftFor(aReadyTalk(MAX));

        assertThat(InvitationText.of(withoutBio, NAMES, "Referent")).isEqualTo("""
                Warum Records mehr sind als weniger Tippen.

                Referent - Max Muster""");
    }

    @Test
    void aTopicWithNothingWrittenYetHasNoInvitation() {
        Event bare = Event.draftFor(Talk.by(TalkSpeaker.of(MAX)));

        assertThat(InvitationText.of(bare, Map.of(), "Referent")).isEmpty();
    }

    private static Talk withBio(Talk talk, String bio) {
        return talk.withSpeakers(talk.speakers().stream()
                .map(announced -> announced.withAnnouncedBio(bio))
                .toList());
    }
}
