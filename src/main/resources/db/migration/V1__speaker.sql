create table speaker
(
    id      bigserial primary key,
    name    text not null,
    company text,
    email   text,
    phone   text,
    bio     text,
    notes   text
);

comment on column speaker.bio is
    'The current biography. What was announced for a given evening is copied into talk_speaker.';

create table speaker_link
(
    speaker_id bigint not null references speaker (id) on delete cascade,
    position   int    not null,
    url        text   not null,
    label      text,
    primary key (speaker_id, position)
);
