package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.notes.Note;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** The slip box. Spring Data implements it. */
public interface NoteRepository extends ListCrudRepository<Note, Long> {

    /** Newest first: the board shows what was just thought of. */
    @Query("select * from note order by written_at desc, id desc")
    List<Note> allNewestFirst();
}
