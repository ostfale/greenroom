package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** The requests that went out to speakers. Spring Data implements it. */
public interface SpeakerInquiryRepository extends ListCrudRepository<SpeakerInquiry, Long> {

    /** Newest first: what went out last is what is being waited for. */
    @Query("select * from speaker_inquiry where event_id = :eventId order by sent_at desc, id desc")
    List<SpeakerInquiry> findByEvent(Long eventId);

    /**
     * Whether that speaker was ever asked. The inquiry keeps pointing at them, so they are
     * kept — the same reason somebody announced on a talk is kept.
     */
    @Query("select exists(select 1 from speaker_inquiry where speaker_id = :speakerId)")
    boolean wasAsked(Long speakerId);
}
