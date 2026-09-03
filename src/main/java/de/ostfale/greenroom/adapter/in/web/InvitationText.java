package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The evening as the text an announcement is written from: the abstract, a blank line, and
 * then whoever gives it with the biography this evening announced them with. One block per
 * talk, so an evening carrying three of them yields three.
 *
 * <p>Built here for the reason {@link CalendarEntry} is: it is a document to be pasted
 * somewhere else, not a page. The one German word in it is handed in from the bundle — the
 * rest is what was written into the evening, unchanged.
 */
final class InvitationText {

    private InvitationText() {
    }

    /**
     * @param speakerNames the names to write, by speaker id
     * @param speakerLabel what to call a speaker — German, and from the message bundle
     */
    static String of(Event event, Map<Long, String> speakerNames, String speakerLabel) {
        List<String> blocks = new ArrayList<>();
        for (Talk talk : event.talks()) {
            List<String> lines = new ArrayList<>();
            if (talk.abstractText() != null) {
                lines.add(talk.abstractText());
                // The blank line the announcement is read across.
                lines.add("");
            }
            for (TalkSpeaker announced : talk.speakers()) {
                String name = speakerNames.get(announced.speakerId());
                if (name != null) {
                    lines.add(speakerLabel + " - " + name);
                }
                if (announced.announcedBio() != null) {
                    lines.add(announced.announcedBio());
                }
            }
            String block = String.join("\n", lines).strip();
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }
        return String.join("\n\n", blocks);
    }
}
