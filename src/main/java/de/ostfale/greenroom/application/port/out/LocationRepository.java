package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.locations.Location;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** Locations, in the words the use cases use. Spring Data implements it. */
public interface LocationRepository extends ListCrudRepository<Location, Long> {

    List<Location> findAllByOrderByNameAsc();

    /** Every address counts, the retired ones too — a place is often remembered by where it was. */
    @Query("""
            select distinct l.* from location l
            left join address a on a.location = l.id
            where l.name ilike '%' || :fragment || '%'
               or a.city ilike '%' || :fragment || '%'
            order by l.name
            """)
    List<Location> search(String fragment);
}
