package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.application.port.in.ManageNotes;
import de.ostfale.greenroom.domain.notes.Note;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The slip box. Two things happen here — something is written down, or thrown away — and
 * the board is what comes back from both.
 */
@Controller
@RequestMapping("/note")
public class NoteController {

    private final ManageNotes notes;

    public NoteController(ManageNotes notes) {
        this.notes = notes;
    }

    @GetMapping
    public String board(Model model) {
        model.addAttribute("notes", notes.all());
        return "note/list";
    }

    /** The same route for htmx: the bare board. */
    @GetMapping(headers = "HX-Request")
    public String boardFragment(Model model) {
        return board(model, null);
    }

    @PostMapping
    public String add(@RequestParam(defaultValue = "") String title,
                      @RequestParam(defaultValue = "") String text,
                      Model model) {
        try {
            notes.add(title, text);
            return "redirect:/note";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", message(e));
            model.addAttribute("submittedTitle", title);
            model.addAttribute("submittedText", text);
            model.addAttribute("notes", notes.all());
            return "note/list";
        }
    }

    /** The same for htmx, so a new slip appears without reloading the page. */
    @PostMapping(headers = "HX-Request")
    public String addFragment(@RequestParam(defaultValue = "") String title,
                              @RequestParam(defaultValue = "") String text,
                              Model model) {
        try {
            notes.add(title, text);
            return board(model, null);
        } catch (IllegalArgumentException e) {
            return board(model, message(e));
        }
    }

    /** One tile, in the state it is read in. Also what "Abbrechen" asks for. */
    @GetMapping("/{id}")
    public String card(@PathVariable Long id, Model model) {
        return tile(model, id, "note-card", null);
    }

    /** The same tile, opened for changing. */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return tile(model, id, "note-editor", null);
    }

    /**
     * A thought put right. It comes back as the card when it worked, and as the editor
     * with what was typed still in it when it did not.
     */
    @PostMapping("/{id}")
    public String change(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String text,
                         Model model) {
        try {
            model.addAttribute("note", notes.change(id, title, text));
            return "fragments/note-board :: note-card";
        } catch (IllegalArgumentException e) {
            // What was typed goes back into the editor, not what is stored.
            return notes.byId(id)
                    .map(stored -> tile(model, id, "note-editor", message(e),
                            stored.withTitle(title.isBlank() ? stored.title() : title)
                                    .withText(text)))
                    .orElse("fragments/note-board :: note-gone");
        }
    }

    /** Gone means gone. Nothing points at a note, so nothing has to be told about it. */
    @PostMapping("/{id}/remove")
    public String remove(@PathVariable Long id, Model model) {
        notes.remove(id);
        return board(model, null);
    }

    private String tile(Model model, Long id, String fragment, String error) {
        return notes.byId(id)
                .map(note -> tile(model, id, fragment, error, note))
                .orElse("fragments/note-board :: note-gone");
    }

    private String tile(Model model, Long id, String fragment, String error, Note note) {
        model.addAttribute("note", note);
        model.addAttribute("error", error);
        return "fragments/note-board :: " + fragment;
    }

    private String board(Model model, String error) {
        model.addAttribute("notes", notes.all());
        model.addAttribute("error", error);
        return "fragments/note-board :: note-board";
    }

    private static String message(IllegalArgumentException e) {
        String reason = e.getMessage() == null ? "" : e.getMessage();
        if (reason.contains("needs a title")) {
            return "Bitte ein Stichwort eingeben — der Text darf leer bleiben.";
        }
        if (reason.contains("there is no note")) {
            return "Diese Notiz gibt es nicht mehr — bitte die Seite neu laden.";
        }
        return "Die Notiz wurde nicht gespeichert.";
    }
}
