package de.ostfale.greenroom;

import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.application.port.out.TagRepository;

/**
 * Empties the database in the order the foreign keys allow.
 *
 * <p>The order is the whole point: a speaker who is announced on a talk cannot be deleted,
 * and neither can a location an evening points at. Every test that wants a clean table
 * asks for {@link #empty()} instead of remembering that.
 */
public class TestDatabase {

    private final EventRepository events;
    private final SpeakerRepository speakers;
    private final LocationRepository locations;
    private final TagRepository tags;

    public TestDatabase(EventRepository events, SpeakerRepository speakers,
                        LocationRepository locations, TagRepository tags) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
    }

    /** The evenings go first; they are what points at the rest. */
    public void empty() {
        events.deleteAll();
        speakers.deleteAll();
        locations.deleteAll();
        tags.deleteAll();
    }
}
