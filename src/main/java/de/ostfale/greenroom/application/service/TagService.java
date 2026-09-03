package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageTags;
import de.ostfale.greenroom.application.port.out.TagRepository;
import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.tags.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    @Transactional(readOnly = true)
    public Optional<Tag> byId(Long id) {
        return id == null ? Optional.empty() : tagRepository.findById(id);
    }

    @Override
    public Tag add(Tag tag) {
        if (tag.id() != null) {
            throw new IllegalArgumentException("TagService :: this tag is already stored");
        }
        // Checked here rather than left to the unique index, so the page can say which word
        // is already taken instead of showing a constraint name.
        tagRepository.findByName(tag.name()).ifPresent(existing -> {
            throw new RuleViolated(Rule.TAG_ALREADY_ON_THE_LIST, existing.name());
        });
        return tagRepository.save(tag);
    }

    @Override
    public Tag rename(Long id, String name) {
        Tag renamed = new Tag(id, name);
        if (!tagRepository.existsById(id)) {
            throw new RuleViolated(Rule.NO_SUCH_TAG, id);
        }
        // The word may stay where it is; only somebody else's word is in the way.
        tagRepository.findByName(renamed.name()).ifPresent(existing -> {
            if (!existing.id().equals(id)) {
                throw new RuleViolated(Rule.TAG_ALREADY_ON_THE_LIST, existing.name());
            }
        });
        return tagRepository.save(renamed);
    }

    @Override
    public void remove(Long id) {
        // No guard: an evening carries the word, not this row. What was announced stays.
        tagRepository.deleteById(id);
    }
}
