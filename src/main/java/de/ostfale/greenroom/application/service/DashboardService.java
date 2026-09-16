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
import de.ostfale.greenroom.domain.events.NextStep;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

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

        List<Speaker> everyone = speakers.findAll();
        Map<Long, String> names = everyone.stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name));

        List<Location> places = locations.findAll();
        Map<Long, String> venues = places.stream()
                .collect(Collectors.toMap(Location::id, Location::name));

        List<Dashboard.Upcoming> dated = all.stream()
                .filter(event -> !event.status().isClosed())
                .filter(event -> event.date() != null)
                .sorted(Comparator.comparing(Event::date))
                .map(event -> upcoming(event, today, names, venues))
                .toList();

        // The top tile carries what is settled: the evenings whose announcement is out, the
        // nearest date first. What is left is what still wants a hand, and only that is
        // "weiter geplant" — an evening nobody has been told about yet.
        List<Dashboard.Upcoming> ahead = dated.stream()
                .filter(DashboardService::isAnnounced).toList();
        List<Dashboard.Upcoming> open = dated.stream()
                .filter(not(DashboardService::isAnnounced)).toList();

        List<Dashboard.Topic> topics = all.stream()
                .filter(event -> !event.status().isClosed())
                .filter(event -> event.date() == null)
                .map(event -> new Dashboard.Topic(event, named(event, names)))
                .toList();

        return new Dashboard(
                ahead,
                open,
                topics,
                counted(all, today, places),
                whereWeHaveBeen(all, places),
                whoWeHaveHad(all, everyone));
    }

    /** The announcement is out. Done and cancelled never reach here — they are not upcoming. */
    private static boolean isAnnounced(Dashboard.Upcoming row) {
        return row.evening().status() == EventStatus.PUBLISHED;
    }

    /**
     * The place is named only where it is the answer: on the step that waits for its yes.
     * Everywhere else the step says all there is to say and a name beside it is noise.
     */
    private static Dashboard.Upcoming upcoming(Event evening, LocalDate today,
                                               Map<Long, String> names,
                                               Map<Long, String> venues) {
        NextStep step = evening.nextStep(today);
        String picked = step == NextStep.CONFIRM_THE_VENUE
                ? venues.get(evening.locationId())
                : null;
        return new Dashboard.Upcoming(evening, step,
                ChronoUnit.DAYS.between(today, evening.date()), named(evening, names), picked);
    }

    private Dashboard.Counts counted(List<Event> all, LocalDate today, List<Location> places) {
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
    private static List<Dashboard.Tally> whereWeHaveBeen(List<Event> all, List<Location> places) {
        return places.stream()
                .map(place -> {
                    List<Event> there = all.stream()
                            .filter(event -> event.isAt(place.id())).toList();
                    return tally(place.id(), place.name(), there.size(), there);
                })
                .filter(tally -> tally.times() > 0)
                .sorted(byHowOftenAndHowRecently())
                .limit(MOST)
                .toList();
    }

    /** In the order the evening holds its people, so the first one named is the first one. */
    private static List<String> named(Event evening, Map<Long, String> names) {
        return evening.speakerIds().stream()
                .map(names::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Counted in talks, not in evenings: two talks on one night are two. */
    private List<Dashboard.Tally> whoWeHaveHad(List<Event> all, List<Speaker> everyone) {
        return everyone.stream()
                .map(person -> {
                    List<Event> given = all.stream()
                            .filter(event -> event.isGivenBy(person.id())).toList();
                    long talks = given.stream()
                            .mapToLong(event -> event.talksBy(person.id())).sum();
                    return tally(person.id(), person.name(), talks, given);
                })
                .filter(tally -> tally.times() > 0)
                .sorted(byHowOftenAndHowRecently())
                .limit(MOST)
                .toList();
    }

    private static Dashboard.Tally tally(Long id, String name, long times, List<Event> evenings) {
        LocalDate last = evenings.stream()
                .map(Event::date)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new Dashboard.Tally(id, name, times, last);
    }

    /** Most often first; among equals the one we were at last, and then by name. */
    private static Comparator<Dashboard.Tally> byHowOftenAndHowRecently() {
        return Comparator.comparingLong(Dashboard.Tally::times).reversed()
                .thenComparing(Dashboard.Tally::last,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Dashboard.Tally::name);
    }
}
