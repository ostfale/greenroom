package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageActivities;
import de.ostfale.greenroom.application.port.out.ActivityRepository;
import de.ostfale.greenroom.domain.activities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The history of an evening, which is nothing but the lines somebody wrote. It used to mix
 * the inquiries in as it read; there are no inquiries any more, and one table is the whole
 * story.
 */
@Service
@Transactional
public class ActivityService implements ManageActivities {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ActivityRepository activities;

    public ActivityService(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public Activity append(Activity activity) {
        if (activity.id() != null) {
            throw new IllegalArgumentException("ActivityService :: this entry is already written");
        }
        log.debug("ActivityService :: {} on event {}", activity.kind(), activity.eventId());
        return activities.save(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Activity> historyOf(Long eventId) {
        return eventId == null ? List.of() : activities.findByEvent(eventId);
    }
}
