package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** The maintained list of tags. Spring Data implements it. */
public interface TagRepository extends ListCrudRepository<Tag, Long> {

    List<Tag> findAllByOrderByNameAsc();

    @Query("select * from tag where lower(name) = lower(:name)")
    Optional<Tag> findByName(String name);
}
