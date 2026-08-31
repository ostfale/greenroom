package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/speaker")
public class SpeakerController {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ManageSpeakers speakers;

    public SpeakerController(ManageSpeakers speakers) {
        this.speakers = speakers;
    }

    /** The whole page, filtered by the search box if it carries anything. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String search, Model model) {
        fill(model, search);
        return "speaker/list";
    }

    /**
     * The same route for htmx: only the table comes back, so typing in the search box
     * replaces it without reloading the page.
     */
    @GetMapping(headers = "HX-Request")
    public String listFragment(@RequestParam(defaultValue = "") String search, Model model) {
        fill(model, search);
        return "fragments/speaker-table :: speaker-table";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("submitted", empty());
        return "speaker/form";
    }

    @PostMapping
    public String add(@RequestParam(defaultValue = "") String name,
                      @RequestParam(defaultValue = "") String email,
                      @RequestParam(defaultValue = "") String company,
                      @RequestParam(defaultValue = "") String phone,
                      @RequestParam(defaultValue = "") String bio,
                      @RequestParam(defaultValue = "") String notes,
                      @RequestParam(required = false) MultipartFile photo,
                      Model model) {
        try {
            speakers.add(new Speaker(null, name, company, email, phone, bio, notes, List.of()),
                    photo == null ? null : photo.getContentType(),
                    photo == null ? null : photo.getBytes());
            return "redirect:/speaker";
        } catch (IOException | IllegalArgumentException e) {
            // The records know the rules; the form only has to say so in German and keep
            // what was typed. The chosen file is gone — no browser lets us put it back.
            model.addAttribute("error", addMessage(e));
            model.addAttribute("submitted", submitted(name, email, company, phone, bio, notes));
            return "speaker/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return speakers.byId(id)
                .map(speaker -> {
                    model.addAttribute("speaker", speaker);
                    model.addAttribute("hasPhoto", speakers.photoOf(id).isPresent());
                    return "speaker/detail";
                })
                .orElse("redirect:/speaker");
    }

    /** The picture itself. Its own route so the list never carries the bytes. */
    @GetMapping("/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return speakers.photoOf(id)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                        .body(photo.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/photo")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam MultipartFile photo,
                              Model model) {
        try {
            speakers.storePhoto(id, photo.getContentType(), photo.getBytes());
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("error", photoMessage(e));
        }
        return photoFragment(id, model);
    }

    @PostMapping("/{id}/photo/remove")
    public String removePhoto(@PathVariable Long id, Model model) {
        speakers.removePhoto(id);
        return photoFragment(id, model);
    }

    private String photoFragment(Long id, Model model) {
        speakers.byId(id).ifPresent(speaker -> model.addAttribute("speaker", speaker));
        model.addAttribute("hasPhoto", speakers.photoOf(id).isPresent());
        return "fragments/speaker-photo :: speaker-photo";
    }

    /** Creating fails either on the speaker or on the picture — say which. */
    private static String addMessage(Exception e) {
        return aboutThePicture(e)
                ? photoMessage(e) + " Der Referent wurde nicht angelegt."
                : "Name und E-Mail-Adresse sind Pflichtfelder.";
    }

    /** Every complaint about the file carries the word, whether it came from the record or the scaler. */
    private static boolean aboutThePicture(Exception e) {
        return e.getMessage() != null && e.getMessage().contains("picture");
    }

    private static String photoMessage(Exception e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("too large")) {
            return "Das Bild ist zu groß — bis zu 2 MB sind möglich.";
        }
        if (reason.contains("not a picture we can show")) {
            return "Nur JPEG, PNG, WebP oder GIF.";
        }
        return "Es war kein Bild in der Datei.";
    }

    private void fill(Model model, String search) {
        model.addAttribute("speakers", speakers.matching(search));
        model.addAttribute("search", search);
    }

    private static Map<String, String> empty() {
        return submitted("", "", "", "", "", "");
    }

    private static Map<String, String> submitted(String name, String email, String company,
                                                 String phone, String bio, String notes) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("email", email);
        values.put("company", company);
        values.put("phone", phone);
        values.put("bio", bio);
        values.put("notes", notes);
        return values;
    }
}
