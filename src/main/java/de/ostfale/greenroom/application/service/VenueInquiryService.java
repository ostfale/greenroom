package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageVenueInquiries;
import de.ostfale.greenroom.application.port.out.MailMessage;
import de.ostfale.greenroom.application.port.out.SendMail;
import de.ostfale.greenroom.application.port.out.VenueInquiryRepository;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.VenueInquiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class VenueInquiryService implements ManageVenueInquiries {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final VenueInquiryRepository inquiryRepository;
    private final SendMail mailer;

    public VenueInquiryService(VenueInquiryRepository inquiryRepository, SendMail mailer) {
        this.inquiryRepository = inquiryRepository;
        this.mailer = mailer;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VenueInquiry> forEvent(Long eventId) {
        return eventId == null ? List.of() : inquiryRepository.findByEvent(eventId);
    }

    @Override
    public VenueInquiry send(VenueInquiry inquiry) {
        if (inquiry.id() != null) {
            throw new IllegalArgumentException("VenueInquiryService :: this inquiry is already stored");
        }
        log.debug("VenueInquiryService :: inquiry to location {} for event {} about {}",
                inquiry.locationId(), inquiry.eventId(), inquiry.forDate());
        return inquiryRepository.save(inquiry);
    }

    @Override
    public VenueInquiry sendByMail(VenueInquiry inquiry, MailMessage mail) {
        // The mail first. A refusal from the server ends this here, and the history stays
        // free of an inquiry that never left the house.
        mailer.send(mail);
        return send(inquiry);
    }

    @Override
    public VenueInquiry answer(Long inquiryId, InquiryOutcome outcome) {
        VenueInquiry known = inquiryRepository.findById(inquiryId).orElseThrow(() ->
                new IllegalArgumentException("VenueInquiryService :: there is no inquiry " + inquiryId));
        log.debug("VenueInquiryService :: inquiry {} answered with {}", inquiryId, outcome);
        return inquiryRepository.save(known.answered(outcome, LocalDate.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VenueInquiry> waitingOn(Long eventId) {
        return forEvent(eventId).stream().filter(VenueInquiry::isOpen).findFirst();
    }
}
