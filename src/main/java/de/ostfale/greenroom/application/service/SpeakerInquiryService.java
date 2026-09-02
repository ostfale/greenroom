package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageSpeakerInquiries;
import de.ostfale.greenroom.application.port.out.SpeakerInquiryRepository;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class SpeakerInquiryService implements ManageSpeakerInquiries {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SpeakerInquiryRepository inquiryRepository;

    public SpeakerInquiryService(SpeakerInquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpeakerInquiry> forEvent(Long eventId) {
        return eventId == null ? List.of() : inquiryRepository.findByEvent(eventId);
    }

    @Override
    public SpeakerInquiry send(SpeakerInquiry inquiry) {
        if (inquiry.id() != null) {
            throw new IllegalArgumentException("SpeakerInquiryService :: this inquiry is already stored");
        }
        log.debug("SpeakerInquiryService :: inquiry to speaker {} for event {}",
                inquiry.speakerId(), inquiry.eventId());
        return inquiryRepository.save(inquiry);
    }

    @Override
    public SpeakerInquiry answer(Long inquiryId, InquiryOutcome outcome) {
        SpeakerInquiry known = inquiryRepository.findById(inquiryId).orElseThrow(() ->
                new IllegalArgumentException("SpeakerInquiryService :: there is no inquiry " + inquiryId));
        log.debug("SpeakerInquiryService :: inquiry {} answered with {}", inquiryId, outcome);
        return inquiryRepository.save(known.answered(outcome, LocalDate.now()));
    }
}
