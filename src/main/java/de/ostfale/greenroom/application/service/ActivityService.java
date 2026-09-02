package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.HistoryEntry;
import de.ostfale.greenroom.application.port.in.ManageActivities;
import de.ostfale.greenroom.application.port.out.ActivityRepository;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.application.port.out.SpeakerInquiryRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.application.port.out.VenueInquiryRepository;
import de.ostfale.greenroom.domain.activities.Activity;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import de.ostfale.greenroom.domain.activities.VenueInquiry;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads the three sources of an evening's history and puts them in one order. It has more
 * dependencies than the other services, and that is the point: the alternative would be to
 * copy every inquiry into the log as it happens, and then keep two records of one fact.
 */
@Service
@Transactional
public class ActivityService implements ManageActivities {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ActivityRepository activities;
    private final SpeakerInquiryRepository speakerInquiries;
    private final VenueInquiryRepository venueInquiries;
    private final SpeakerRepository speakers;
    private final LocationRepository locations;

    public ActivityService(ActivityRepository activities,
                           SpeakerInquiryRepository speakerInquiries,
                           VenueInquiryRepository venueInquiries,
                           SpeakerRepository speakers,
                           LocationRepository locations) {
        this.activities = activities;
        this.speakerInquiries = speakerInquiries;
        this.venueInquiries = venueInquiries;
        this.speakers = speakers;
        this.locations = locations;
    }

    @Override
    public Activity append(Activity activity) {
        if (activity.id() != null) {
            throw new IllegalArgumentException("ActivityService :: this entry is already written");
        }
        log.debug("ActivityService :: {} on event {}", activity.direction(), activity.eventId());
        return activities.save(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryEntry> historyOf(Long eventId) {
        if (eventId == null) {
            return List.of();
        }
        Map<Long, String> speakerNames = speakers.findAll().stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name));
        Map<Long, String> placeNames = locations.findAll().stream()
                .collect(Collectors.toMap(Location::id, Location::name));

        List<HistoryEntry> lines = new ArrayList<>();
        for (SpeakerInquiry inquiry : speakerInquiries.findByEvent(eventId)) {
            String who = speakerNames.get(inquiry.speakerId());
            lines.add(HistoryEntry.asked(inquiry.sentAt(), who, inquiry.askedAbout()));
            if (!inquiry.isOpen()) {
                lines.add(HistoryEntry.answered(inquiry.answeredOn(), who, inquiry.outcome()));
            }
        }
        for (VenueInquiry inquiry : venueInquiries.findByEvent(eventId)) {
            String who = placeNames.get(inquiry.locationId());
            lines.add(HistoryEntry.asked(inquiry.sentAt(), who, inquiry.forDate()));
            if (!inquiry.isOpen()) {
                lines.add(HistoryEntry.answered(inquiry.answeredOn(), who, inquiry.outcome()));
            }
        }
        activities.findByEvent(eventId).forEach(entry -> lines.add(HistoryEntry.from(entry)));

        // Stable, so entries of one day keep the order they were collected in: what was
        // asked before what came back, and the hand-written lines last.
        lines.sort(Comparator.comparing(HistoryEntry::on));
        return List.copyOf(lines);
    }
}
