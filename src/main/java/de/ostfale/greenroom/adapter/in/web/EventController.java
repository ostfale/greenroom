package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageEvents;
import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.in.ManageSpeakerInquiries;
import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.application.port.in.ManageVenueInquiries;
import de.ostfale.greenroom.domain.activities.ContactChannel;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import de.ostfale.greenroom.domain.activities.VenueInquiry;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;
import de.ostfale.greenroom.domain.events.TalkSpeaker;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import de.ostfale.greenroom.domain.speakers.Speaker;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The evenings. Creating one means creating its first talk with its speaker at the same
 * time — an event without a talk, and a talk without a speaker, do not exist.
 */
@Controller
@RequestMapping("/event")
public class EventController {

    private final ManageEvents events;
    private final ManageSpeakers speakers;
    private final ManageLocations locations;
    private final ManageTags tags;
    private final ManageSpeakerInquiries inquiries;
    private final ManageVenueInquiries venueInquiries;

    public EventController(ManageEvents events, ManageSpeakers speakers, ManageLocations locations,
                           ManageTags tags, ManageSpeakerInquiries inquiries,
                           ManageVenueInquiries venueInquiries) {
        this.events = events;
        this.speakers = speakers;
        this.locations = locations;
        this.tags = tags;
        this.inquiries = inquiries;
        this.venueInquiries = venueInquiries;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean hideClosed, Model model) {
        fill(model, hideClosed);
        return "event/list";
    }

    /** The same route for htmx: only the table comes back when the filter is toggled. */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "false") boolean hideClosed, Model model) {
        fill(model, hideClosed);
        return "fragments/event-table :: event-table";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("submitted", submitted("", "", ""));
        model.addAttribute("speakers", speakers.all());
        return "event/form";
    }

    @PostMapping
    public String add(@RequestParam(defaultValue = "") String speakerId,
                      @RequestParam(defaultValue = "") String title,
                      @RequestParam(defaultValue = "") String date,
                      Model model) {
        try {
            Talk talk = Talk.by(announced(speakerId)).withTitle(title);
            events.add(Event.draftFor(talk).withDate(evening(date)));
            return "redirect:/event";
        } catch (IllegalArgumentException e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", message(e));
            model.addAttribute("submitted", submitted(speakerId, title, date));
            model.addAttribute("speakers", speakers.all());
            return "event/form";
        }
    }

    /**
     * A talk speaker with the biography they have right now. The copy is taken here, at
     * the moment the person is put on the talk: what the evening announced stays, however
     * often they rewrite their bio afterwards.
     */
    private TalkSpeaker announced(String speakerId) {
        Long id = speaker(speakerId);
        return speakers.byId(id)
                .map(TalkSpeaker::announcing)
                .orElseThrow(() -> new IllegalArgumentException("Event :: no speaker was chosen"));
    }

    private static Long speaker(String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("Event :: no speaker was chosen");
        }
        try {
            return Long.valueOf(speakerId.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Event :: no speaker was chosen");
        }
    }

    /** Empty means the date is still open — a topic is allowed to have none. */
    private static LocalDate evening(String date) {
        return FormValues.date(date);
    }

    private static String message(IllegalArgumentException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("not a date")) {
            return "Das Datum konnte nicht gelesen werden.";
        }
        return "Bitte einen Referenten auswählen.";
    }

    private void fill(Model model, boolean hideClosed) {
        model.addAttribute("events", hideClosed ? events.allStillOpen() : events.all());
        model.addAttribute("hideClosed", hideClosed);
        model.addAttribute("locationNames", locations.all().stream()
                .collect(Collectors.toMap(Location::id, Location::name)));
    }

    private static Map<String, String> submitted(String speakerId, String title, String date) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("speakerId", speakerId);
        values.put("title", title);
        values.put("date", date);
        return values;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return events.byId(id)
                .map(event -> {
                    show(model, event);
                    return "event/detail";
                })
                .orElse("redirect:/event");
    }

    /**
     * Date and motto — the two things an evening carries on its own. Everything the status
     * promises beyond them is a step of its own.
     */
    @PostMapping("/{id}")
    public String change(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String date,
                         @RequestParam(defaultValue = "") String motto,
                         @RequestParam(defaultValue = "") String moderator,
                         @RequestParam(defaultValue = "") String notes,
                         Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            events.change(known.withDate(evening(date))
                    .withMotto(motto)
                    .withModerator(moderator)
                    .withNotes(notes));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-basics :: event-basics");
    }

    /**
     * One step on. Only the target is sent: which status the evening has is read from the
     * database, so a page left open overnight cannot talk it into a step it never had.
     */
    @PostMapping("/{id}/status")
    public String moveTo(@PathVariable Long id, @RequestParam EventStatus target, Model model) {
        try {
            events.moveTo(id, target);
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-status :: event-status");
    }

    /**
     * The host. Empty means the evening has none yet — or lost the one it had, which the
     * record refuses once the status promises a venue.
     */
    @PostMapping("/{id}/location")
    public String assignVenue(@PathVariable Long id,
                              @RequestParam(defaultValue = "") String locationId,
                              Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            events.change(known.withLocation(venue(locationId)));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-venue :: event-venue");
    }

    /** Empty is a valid answer here: an evening may well have no venue yet. */
    private static Long venue(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(locationId.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Event :: no location was chosen");
        }
    }

    /**
     * The words this evening is announced with. Copied from the list in the settings, never
     * referenced: renaming or deleting a tag later must not rewrite what an evening was.
     */
    @PostMapping("/{id}/tags")
    public String changeTags(@PathVariable Long id,
                             @RequestParam(name = "tag", required = false) List<String> chosen,
                             Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            events.change(known.withTags(chosen == null ? List.of() : chosen));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-tags :: event-tags");
    }

    /** A further talk. Like the first one, it comes into being with its speaker. */
    @PostMapping("/{id}/talk")
    public String addTalk(@PathVariable Long id,
                          @RequestParam(defaultValue = "") String speakerId,
                          @RequestParam(defaultValue = "") String title,
                          Model model) {
        return talks(id, model, () -> events.addTalk(id, Talk.by(announced(speakerId)).withTitle(title)));
    }

    @PostMapping("/{id}/talk/{position}")
    public String changeTalk(@PathVariable Long id,
                             @PathVariable int position,
                             @RequestParam(defaultValue = "") String title,
                             @RequestParam(defaultValue = "") String abstractText,
                             @RequestParam(name = "announcedBio", required = false) List<String> bios,
                             Model model) {
        return talks(id, model, () -> events.changeTalk(id, position, title, abstractText, bios));
    }

    @PostMapping("/{id}/talk/{position}/remove")
    public String removeTalk(@PathVariable Long id, @PathVariable int position, Model model) {
        return talks(id, model, () -> events.removeTalk(id, position));
    }

    /**
     * Every change to the talks answers with the same list. On a refusal the stored state
     * comes back unchanged, together with the reason.
     */
    private String talks(Long id, Model model, Supplier<Event> change) {
        try {
            change.get();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-talks :: event-talks");
    }

    /** An inquiry that has gone out: to whom, when, and how it was sent. */
    @PostMapping("/{id}/inquiry")
    public String sendInquiry(@PathVariable Long id,
                              @RequestParam(defaultValue = "") String speakerId,
                              @RequestParam(defaultValue = "") String channel,
                              @RequestParam(defaultValue = "") String sentAt,
                              @RequestParam(defaultValue = "") String note,
                              Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            inquiries.send(SpeakerInquiry
                    .sent(id, speaker(speakerId), known.date(), day(sentAt), how(channel))
                    .withNote(note));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-inquiries :: event-inquiries");
    }

    /** What came back. A refusal is not a correction of the inquiry — it is its answer. */
    @PostMapping("/{id}/inquiry/{inquiryId}")
    public String answerInquiry(@PathVariable Long id,
                                @PathVariable Long inquiryId,
                                @RequestParam InquiryOutcome outcome,
                                Model model) {
        try {
            inquiries.answer(inquiryId, outcome);
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-inquiries :: event-inquiries");
    }

    /**
     * An inquiry that went out to a place. The date is not asked for: the evening already
     * has one, and it is copied onto the inquiry as it stands right now.
     */
    @PostMapping("/{id}/venue-inquiry")
    public String sendVenueInquiry(@PathVariable Long id,
                                   @RequestParam(defaultValue = "") String locationId,
                                   @RequestParam(defaultValue = "") String contactName,
                                   @RequestParam(defaultValue = "") String channel,
                                   @RequestParam(defaultValue = "") String sentAt,
                                   @RequestParam(defaultValue = "") String note,
                                   Model model) {
        try {
            Event known = events.byId(id).orElseThrow(() ->
                    new IllegalArgumentException("EventController :: unknown event"));
            venueInquiries.send(VenueInquiry
                    .sent(id, askedPlace(locationId), contactName, known.date(), day(sentAt), how(channel))
                    .withNote(note));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-venue-inquiries :: event-venue-inquiries");
    }

    /** What the place answered. Asking the next one is a new inquiry, and both stay. */
    @PostMapping("/{id}/venue-inquiry/{inquiryId}")
    public String answerVenueInquiry(@PathVariable Long id,
                                     @PathVariable Long inquiryId,
                                     @RequestParam InquiryOutcome outcome,
                                     Model model) {
        try {
            venueInquiries.answer(inquiryId, outcome);
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", planningMessage(e));
        }
        return tile(id, model, "fragments/event-venue-inquiries :: event-venue-inquiries");
    }

    /**
     * The people to write to at the place that was just picked. Its own little route
     * because the second select depends on the first, and htmx swaps it rather than the
     * whole tile — swapping the tile would fold the form away mid-entry.
     */
    @GetMapping("/{id}/venue-inquiry/contacts")
    public String venueContacts(@PathVariable Long id,
                                @RequestParam(defaultValue = "") String locationId,
                                Model model) {
        model.addAttribute("venueContacts", contactsAt(venue(locationId)));
        return "fragments/event-venue-inquiries :: venue-contacts";
    }

    /** Unlike assigning a venue, asking one needs a place: there is nobody to write to else. */
    private static Long askedPlace(String locationId) {
        Long place = venue(locationId);
        if (place == null) {
            throw new IllegalArgumentException("Event :: no location was chosen");
        }
        return place;
    }

    private List<ContactPerson> contactsAt(Long locationId) {
        return locations.byId(locationId).map(Location::contacts).orElse(List.of());
    }

    /** Empty means today: an inquiry is written down right after it went out. */
    private static LocalDate day(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        return evening(date);
    }

    private static ContactChannel how(String channel) {
        try {
            return ContactChannel.valueOf(channel.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry needs a channel");
        }
    }

    /** The people who speak at this evening — those are the ones there is anything to ask. */
    private static List<Long> speakersOf(Event event) {
        List<Long> asked = new ArrayList<>();
        event.talks().forEach(talk -> talk.speakers().forEach(announced -> {
            if (!asked.contains(announced.speakerId())) {
                asked.add(announced.speakerId());
            }
        }));
        return asked;
    }

    /**
     * Whether everybody who speaks has said yes to a date. A hint on the page, not a rule:
     * the step to DATE_CONFIRMED stays something somebody decides.
     */
    private static boolean allAccepted(List<Long> asked, List<SpeakerInquiry> answers) {
        return !asked.isEmpty() && asked.stream().allMatch(speakerId ->
                answers.stream().anyMatch(inquiry ->
                        inquiry.speakerId().equals(speakerId) && inquiry.isAccepted()));
    }

    /** Every change answers with the tile it was made in, showing what is stored now. */
    private String tile(Long id, Model model, String fragment) {
        events.byId(id).ifPresent(event -> show(model, event));
        return fragment;
    }

    private void show(Model model, Event event) {
        List<Location> places = locations.all();
        model.addAttribute("event", event);
        model.addAttribute("transitions", event.status().allowedTargets());
        model.addAttribute("locations", places);
        model.addAttribute("clashes", events.clashesWith(event));
        model.addAttribute("tagChoices", tagChoices(event));
        List<Speaker> known = speakers.all();
        List<SpeakerInquiry> answers = inquiries.forEvent(event.id());
        List<Long> asked = speakersOf(event);
        model.addAttribute("inquiries", answers);
        model.addAttribute("venueInquiries", venueInquiries.forEvent(event.id()));
        // The place the evening is waiting on. Shown before the next inquiry goes out, and
        // shown only — asking two places at once stays the planner's call.
        model.addAttribute("waitingOn", venueInquiries.waitingOn(event.id()).orElse(null));
        model.addAttribute("locationNames", places.stream()
                .collect(Collectors.toMap(Location::id, Location::name)));
        // Filled by its own route once a place is picked; the form opens with none.
        model.addAttribute("venueContacts", List.<ContactPerson>of());
        // In the order of the talks, not alphabetically: the evening reads that way.
        model.addAttribute("eventSpeakers", asked.stream()
                .flatMap(speaking -> known.stream().filter(one -> one.id().equals(speaking)))
                .toList());
        model.addAttribute("allAccepted", allAccepted(asked, answers));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("channels", ContactChannel.values());
        model.addAttribute("speakers", known);
        model.addAttribute("speakerNames", known.stream()
                .collect(Collectors.toMap(Speaker::id, Speaker::name)));
        locations.byId(event.locationId())
                .ifPresent(location -> model.addAttribute("location", location));
    }

    /**
     * Every word that may be ticked: the maintained list, and on top of it whatever this
     * evening already carries. A tag deleted from the settings must not silently fall off
     * an evening that was announced with it.
     */
    private List<String> tagChoices(Event event) {
        List<String> choices = new ArrayList<>(tags.all().stream().map(Tag::name).toList());
        for (String own : event.tags()) {
            if (choices.stream().noneMatch(word -> word.equalsIgnoreCase(own))) {
                choices.add(own);
            }
        }
        return choices;
    }

    /** The records refuse in English; the page has to say in German what is missing. */
    private static String planningMessage(RuntimeException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("already answered")) {
            return "Diese Anfrage ist schon beantwortet. Für einen neuen Versuch bitte eine neue Anfrage anlegen.";
        }
        if (reason.contains("PENDING is not an answer")) {
            return "Bitte eine Antwort auswählen.";
        }
        if (reason.contains("an inquiry needs a channel")) {
            return "Bitte angeben, auf welchem Weg gefragt wurde.";
        }
        if (reason.contains("a place is asked about a date")) {
            return "Zuerst braucht das Event einen Termin — danach werden die Orte gefragt.";
        }
        if (reason.contains("there is no inquiry")) {
            return "Diese Anfrage gibt es nicht mehr — bitte die Seite neu laden.";
        }
        if (reason.contains("is on this event twice")) {
            return "Dieses Schlagwort steht schon an diesem Event.";
        }
        if (reason.contains("a tag needs a word")) {
            return "Ein Schlagwort braucht ein Wort.";
        }
        if (reason.contains("at least one talk")) {
            return "Der letzte Vortrag kann nicht entfernt werden — ohne ihn ist es kein Event.";
        }
        if (reason.contains("no talk at position")) {
            return "Diesen Vortrag gibt es nicht mehr — bitte die Seite neu laden.";
        }
        if (reason.contains("no speaker was chosen")) {
            return "Bitte einen Referenten auswählen.";
        }
        if (reason.contains("no location was chosen")) {
            return "Bitte einen Ort auswählen.";
        }
        if (reason.contains("does not move to")) {
            return "Dieser Schritt ist von hier aus nicht möglich.";
        }
        if (reason.contains("needs a date")) {
            return "Dafür braucht das Event ein Datum.";
        }
        if (reason.contains("needs a location")) {
            return "Dafür braucht das Event einen Ort.";
        }
        if (reason.contains("needs a title and an abstract")) {
            return "Dafür braucht jeder Vortrag einen Titel und eine Beschreibung.";
        }
        if (reason.contains("not a date")) {
            return "Das Datum konnte nicht gelesen werden.";
        }
        return "Die Änderung wurde nicht übernommen.";
    }
}
