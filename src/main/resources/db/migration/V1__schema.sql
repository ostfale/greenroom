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
    notes       text,
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
    answered_on date,
    note        text
);

create index speaker_inquiry_event on speaker_inquiry (event_id);

comment on column speaker_inquiry.answered_on is
    'Set with the answer and only with it: PENDING is null, an answer is dated.';

-- The second question of an evening: the speakers have said yes, so the day is set, and
-- what is asked is a place. The mirror image of speaker_inquiry, and a table of its own for
-- the same reason it is an aggregate of its own.
create table venue_inquiry
(
    id           bigserial primary key,
    event_id     bigint not null references event (id) on delete cascade,
    location_id  bigint not null references location (id),
    contact_name text,
    for_date     date   not null,
    sent_at      date   not null,
    channel      text   not null,
    outcome      text   not null,
    answered_on  date,
    note         text
);

create index venue_inquiry_event on venue_inquiry (event_id);

comment on column venue_inquiry.for_date is
    'Not null, unlike speaker_inquiry.asked_about: a place is asked about a day that is set.';

comment on column venue_inquiry.contact_name is
    'Whom we wrote to, copied. A contact person who leaves must not rewrite who was asked.';

comment on column venue_inquiry.location_id is
    'No cascade on purpose: a place that was once asked is kept.';

-- What happened and has no field of its own. Append-only: there is no update and no delete
-- on this table, only the cascade when the evening goes. What the inquiries already record
-- is not copied here — the history mixes the two when it is shown.
create table activity
(
    id          bigserial primary key,
    event_id    bigint not null references event (id) on delete cascade,
    happened_on date   not null,
    direction   text   not null,
    channel     text,
    what        text   not null
);

create index activity_event on activity (event_id);

comment on column activity.channel is
    'Null exactly for a NOTE: a note went nowhere, so there is no way it went.';

-- The slip box. Points at nothing and nothing points at it: an idea is written down before
-- there is an evening to file it under.
create table note
(
    id         bigserial primary key,
    written_at timestamp not null,
    title      text      not null,
    text       text
);

create index note_written_at on note (written_at desc);

comment on table note is
    'Unlike activity these may be thrown away: a note records what was thought, not what happened.';
