package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.speakers.Speaker;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

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
     * Whoever is already there under that address or under that name. Case does not
     * count: nobody types a name in twice the same way.
     */
    @Query("""
            select * from speaker
            where lower(email) = lower(:email)
               or lower(name) = lower(:name)
            order by name
            """)
    List<Speaker> sameEmailOrName(String name, String email);
}
