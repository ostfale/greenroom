package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What is set once and then used everywhere. For now that is the list of tags; lead times
 * and the rest of the planning settings will join it here.
 *
 * <p>The words are shown as a row of chips. Picking one opens it underneath, so a list of
 * thirty keywords stays a list and not thirty forms.
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final ManageTags tags;
    private final ErrorMessages errors;

    public SettingsController(ManageTags tags, ErrorMessages errors) {
        this.tags = tags;
        this.errors = errors;
    }

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("tags", tags.all());
        return "settings/index";
    }

    /** The same route for htmx: the bare list, which is also what "Abbrechen" asks for. */
    @GetMapping(headers = "HX-Request")
    public String settingsFragment(Model model) {
        return list(model);
    }

    @PostMapping("/tag")
    public String addTag(@RequestParam(defaultValue = "") String name, Model model) {
        try {
            tags.add(Tag.named(name));
            return "redirect:/settings";
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
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
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
        }
        return list(model);
    }

    /** One word, opened for editing under the row of chips. */
    @GetMapping("/tag/{id}")
    public String editTag(@PathVariable Long id, Model model) {
        return tags.byId(id)
                .map(tag -> editor(model, tag.id(), tag.name()))
                .orElseGet(() -> {
                    model.addAttribute("error", errors.text("rule." + Rule.NO_SUCH_TAG));
                    return "fragments/tag-editor :: tag-editor";
                });
    }

    /**
     * A refusal comes back as the editor, with what was typed still in it; a rename that
     * worked comes back as the list, and the editor closes with it.
     */
    @PostMapping("/tag/{id}")
    public String renameTag(@PathVariable Long id,
                            @RequestParam(defaultValue = "") String name,
                            Model model) {
        try {
            tags.rename(id, name);
        } catch (RuleViolated e) {
            model.addAttribute("error", errors.german(e));
            return editor(model, id, name);
        }
        return list(model);
    }

    /**
     * Drops the word from the list. Nothing else has to happen: an evening stores the word
     * it was announced with, not a reference to this row.
     */
    @PostMapping("/tag/{id}/remove")
    public String removeTag(@PathVariable Long id, Model model) {
        tags.remove(id);
        return list(model);
    }

    private String list(Model model) {
        model.addAttribute("tags", tags.all());
        return "fragments/tag-list :: tag-list";
    }

    private static String editor(Model model, Long id, String name) {
        model.addAttribute("tagId", id);
        model.addAttribute("tagName", name);
        return "fragments/tag-editor :: tag-editor";
    }

}
