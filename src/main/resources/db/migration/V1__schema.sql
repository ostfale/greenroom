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

create table location
(
    id    bigserial primary key,
    name  text not null,
    notes text
);

-- A place keeps every address it ever had; only the active flag moves.
create table address
(
    location     bigint  not null references location (id) on delete cascade,
    location_key int     not null,
    street       text,
    postal_code  text,
    city         text,
    capacity     int,
    active       boolean not null,
    primary key (location, location_key)
);

comment on table address is
    'An evening held at an old address was held there. The address is kept, not overwritten.';

comment on column address.capacity is
    'Seats at this address. A place that moves rarely keeps the same room.';

comment on table location is
    'A place that hosts an evening. Exists independently of any event and is reused for years.';

create table contact_person
(
    location     bigint not null references location (id) on delete cascade,
    location_key int    not null,
    name         text   not null,
    email        text   not null,
    phone        text,
    primary key (location, location_key)
);

comment on column contact_person.email is
    'Not optional: a host who cannot be written to cannot be asked for the room.';

create table event
(
    id          bigserial primary key,
    date        date,
    motto       text,
    moderator   text,
    status      text   not null,
    mode        text   not null,
    location_id bigint references location (id),
    tags        text[] not null default '{}'
);

comment on table event is
    'One evening. Has no title: it is called by its motto, otherwise by the title of its talk.';

comment on column event.date is
    'Null while the evening is still a topic. A DRAFT has no date.';

comment on column event.tags is
    'The keywords as they were picked, copied from the list in the settings. Renaming or
     deleting a tag there must not rewrite what an evening was announced with.';

create table talk
(
    id            bigserial primary key,
    event         bigint not null references event (id) on delete cascade,
    event_key     int    not null,
    title         text,
    abstract_text text,
    unique (event, event_key)
);

comment on column event.motto is
    'Optional name for the evening, used when it carries several talks.';

create table talk_speaker
(
    talk          bigint not null references talk (id) on delete cascade,
    talk_key      int    not null,
    speaker_id    bigint not null references speaker (id),
    announced_bio text,
    primary key (talk, talk_key)
);

comment on column talk_speaker.speaker_id is
    'No cascade on purpose: a speaker who once gave a talk cannot be deleted.';

-- The maintained list of keywords, edited in the settings.
create table tag
(
    id   bigserial primary key,
    name text not null
);

create unique index tag_name_unique on tag (lower(name));

comment on index tag_name_unique is
    'Two tags that differ only in case are the same tag.';


create table speaker_photo
(
    id           bigserial primary key,
    speaker_id   bigint not null unique references speaker (id) on delete cascade,
    content_type text   not null,
    data         bytea  not null
);

comment on table speaker_photo is
    'Apart from the speaker on purpose: the list of speakers must not carry the bytes along.';

-- The first question of an evening: the person is on the talk, the date is what is asked.
-- asked_about is a copy of the proposed date, so a refusal keeps what was proposed.
create table speaker_inquiry
(
    id          bigserial primary key,
    event_id    bigint not null references event (id) on delete cascade,
    speaker_id  bigint not null references speaker (id),
    asked_about date,
    sent_at     date   not null,
    channel     text   not null,
    outcome     text   not null,
    note        text
);

create index speaker_inquiry_event on speaker_inquiry (event_id);
