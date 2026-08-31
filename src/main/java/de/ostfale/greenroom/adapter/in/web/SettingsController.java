package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.tag.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What is set once and then used everywhere. For now that is the list of tags; lead times
 * and the rest of the planning settings will join it here.
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final ManageTags tags;

    public SettingsController(ManageTags tags) {
        this.tags = tags;
    }

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("tags", tags.all());
        return "settings/index";
    }

    @PostMapping("/tag")
    public String addTag(@RequestParam(defaultValue = "") String name, Model model) {
        try {
            tags.add(Tag.named(name));
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", message(e, name));
            model.addAttribute("submittedName", name);
            model.addAttribute("tags", tags.all());
            return "settings/index";
        }
    }

    /** The same list for htmx, so a new tag appears without reloading the page. */
    @PostMapping(value = "/tag", headers = "HX-Request")
    public String addTagFragment(@RequestParam(defaultValue = "") String name, Model model) {
        try {
            tags.add(Tag.named(name));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", message(e, name));
        }
        model.addAttribute("tags", tags.all());
        return "fragments/tag-list :: tag-list";
    }

    private static String message(IllegalArgumentException e, String name) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("already on the list")) {
            return "Das Schlagwort " + name.strip() + " steht schon auf der Liste.";
        }
        return "Bitte ein Schlagwort eingeben.";
    }
}
