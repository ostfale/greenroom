package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageSpeakers;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
                      Model model) {
        try {
            speakers.add(new Speaker(null, name, company, email, phone, bio, notes, List.of()));
            return "redirect:/speaker";
        } catch (IllegalArgumentException e) {
            // The record knows the rule; the form only has to say so in German and keep
            // what was typed.
            model.addAttribute("error", "Name und E-Mail-Adresse sind Pflichtfelder.");
            model.addAttribute("submitted", submitted(name, email, company, phone, bio, notes));
            return "speaker/form";
        }
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
