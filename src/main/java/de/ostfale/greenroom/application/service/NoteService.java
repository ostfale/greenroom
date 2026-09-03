package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageNotes;
import de.ostfale.greenroom.application.port.out.NoteRepository;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.notes.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NoteService implements ManageNotes {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Note> all() {
        return noteRepository.allNewestFirst();
    }

    @Override
    public Note add(String title, String text) {
        log.debug("NoteService :: a note was written down");
        return noteRepository.save(Note.written(LocalDateTime.now(), title, text));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Note> byId(Long id) {
        return id == null ? Optional.empty() : noteRepository.findById(id);
    }

    @Override
    public Note change(Long id, String title, String text) {
        Note known = byId(id).orElseThrow(() ->
                new RuleViolated(Rule.NO_SUCH_NOTE, id));
        return noteRepository.save(known.withTitle(title).withText(text));
    }

    @Override
    public void remove(Long id) {
        noteRepository.deleteById(id);
    }
}
