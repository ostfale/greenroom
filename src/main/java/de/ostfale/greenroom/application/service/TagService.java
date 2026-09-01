package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.application.port.out.TagRepository;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TagService implements ManageTags {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tag> all() {
        return tagRepository.findAllByOrderByNameAsc();
    }

    @Override
    public Tag add(Tag tag) {
        if (tag.id() != null) {
            throw new IllegalArgumentException("TagService :: this tag is already stored");
        }
        // Checked here rather than left to the unique index, so the page can say which word
        // is already taken instead of showing a constraint name.
        tagRepository.findByName(tag.name()).ifPresent(existing -> {
            throw new IllegalArgumentException("TagService :: the tag " + existing.name() + " is already on the list");
        });
        return tagRepository.save(tag);
    }
}
