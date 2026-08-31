-- The whole schema, in one script. While the application is in development this file is
-- extended in place instead of adding V2, V3, … — there is no installation to migrate yet.
-- The dev profile drops and rebuilds the schema when this file changed.

-- Column names follow the Spring Data JDBC defaults, so the entities need no @Table and no
-- @MappedCollection: speaker_link.speaker is the back reference, speaker_key the list index.

create table speaker
(
    id      bigserial primary key,
    name    text not null,
    email   text not null,
    company text,
    phone   text,
    bio     text,
    notes   text
);

comment
on column speaker.email is 'Not optional: a speaker who cannot be written to cannot be asked to give a talk.';

comment
on column speaker.bio is 'The current biography. What was announced for a given evening is copied into talk_speaker.';

create table speaker_link
(
    speaker     bigint not null references speaker (id) on delete cascade,
    speaker_key int    not null,
    url         text   not null,
    label       text,
    primary key (speaker, speaker_key)
);

comment
on column speaker_link.speaker_key is 'Position in the list, kept by Spring Data JDBC.';
