package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/**
 * Speakers, in the words the use cases use. Spring Data implements it; there is no
 * hand-written adapter, because there would be nothing in it but delegation.
 */
public interface SpeakerRepository extends ListCrudRepository<Speaker, Long> {

    List<Speaker> findAllByOrderByNameAsc();

    @Query("""
            select * from speaker
            where name ilike '%' || :fragment || '%'
               or company ilike '%' || :fragment || '%'
            order by name
            """)
    List<Speaker> search(String fragment);

    /**
     * The person behind that address. Used when entering a past evening: somebody who
     * spoke before is recognised instead of being written down a second time.
     */
    @Query("select * from speaker where lower(email) = lower(:email)")
    Optional<Speaker> findByEmail(String email);
}
