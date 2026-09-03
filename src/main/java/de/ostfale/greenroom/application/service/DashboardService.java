package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.Dashboard;
import de.ostfale.greenroom.application.port.in.ShowDashboard;
import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.application.port.out.NoteRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.application.port.out.TagRepository;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.locations.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Counts what there is and says what is next. Everything is worked out in memory from the
 * lists that are loaded anyway — the same trade {@link EventService#matching} makes, and
 * for the same reason: a few hundred rows, against five queries assembled from pieces.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService implements ShowDashboard {

    /** Enough of a ranking to be useful, short enough to stay a glance. */
    private static final int MOST = 10;

    private final EventRepository events;
    private final SpeakerRepository speakers;
    private final LocationRepository locations;
    private final TagRepository tags;
    private final NoteRepository notes;

    public DashboardService(EventRepository events, SpeakerRepository speakers,
                            LocationRepository locations, TagRepository tags,
                            NoteRepository notes) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
        this.notes = notes;
    }

    @Override
    public Dashboard asOf(LocalDate today) {
        List<Event> all = events.allNewestFirst();

        List<Dashboard.Upcoming> dated = all.stream()
                .filter(event -> !event.status().isClosed())
                .filter(event -> event.date() != null)
                .sorted(Comparator.comparing(Event::date))
                .map(event -> new Dashboard.Upcoming(event, event.nextStep(today),
                        ChronoUnit.DAYS.between(today, event.date())))
                .toList();

        List<Event> topics = all.stream()
                .filter(event -> !event.status().isClosed())
                .filter(event -> event.date() == null)
                .toList();

        return new Dashboard(
                dated.isEmpty() ? null : dated.getFirst(),
                dated.isEmpty() ? List.of() : dated.subList(1, dated.size()),
                topics,
                counted(all, today),
                whereWeHaveBeen(all),
                whoWeHaveHad(all));
    }

    private Dashboard.Counts counted(List<Event> all, LocalDate today) {
        List<Location> places = locations.findAll();
        return new Dashboard.Counts(
                all.size(),
                all.stream().filter(event -> event.isIn(today.getYear())).count(),
                all.stream().filter(event -> event.status() == EventStatus.DONE).count(),
                speakers.count(),
                places.size(),
                places.stream().filter(Location::inUse).count(),
                tags.count(),
                notes.count());
    }

    /** The places that hosted at least one evening. A place we never went to is no tally. */
    private List<Dashboard.Tally> whereWeHaveBeen(List<Event> all) {
        return locations.findAll().stream()
                .map(place -> tally(place.id(), place.name(),
                        all.stream().filter(event -> event.isAt(place.id())).toList()))
                .filter(tally -> tally.evenings() > 0)
                .sorted(byHowOftenAndHowRecently())
                .limit(MOST)
                .toList();
    }

    private List<Dashboard.Tally> whoWeHaveHad(List<Event> all) {
        return speakers.findAll().stream()
                .map(person -> tally(person.id(), person.name(),
                        all.stream().filter(event -> event.isGivenBy(person.id())).toList()))
                .filter(tally -> tally.evenings() > 0)
                .sorted(byHowOftenAndHowRecently())
                .limit(MOST)
                .toList();
    }

    private static Dashboard.Tally tally(Long id, String name, List<Event> evenings) {
        LocalDate last = evenings.stream()
                .map(Event::date)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new Dashboard.Tally(id, name, evenings.size(), last);
    }

    /** Most often first; among equals the one we were at last, and then by name. */
    private static Comparator<Dashboard.Tally> byHowOftenAndHowRecently() {
        return Comparator.comparingLong(Dashboard.Tally::evenings).reversed()
                .thenComparing(Dashboard.Tally::last,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Dashboard.Tally::name);
    }
}
