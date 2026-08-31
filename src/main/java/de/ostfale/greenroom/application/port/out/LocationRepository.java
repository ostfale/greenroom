package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.location.Location;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** Locations, in the words the use cases use. Spring Data implements it. */
public interface LocationRepository extends ListCrudRepository<Location, Long> {

    List<Location> findAllByOrderByNameAsc();

    @Query("""
            select * from location
            where name ilike '%' || :fragment || '%'
               or city ilike '%' || :fragment || '%'
            order by name
            """)
    List<Location> search(String fragment);
}
