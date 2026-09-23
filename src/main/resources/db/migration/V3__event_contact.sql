create table event_contact
(
    event     bigint not null references event (id) on delete cascade,
    event_key int    not null,
    name      text   not null,
    email     text   not null,
    phone     text,
    primary key (event, event_key)
);

comment on table event_contact is
    'Who was asked for the room for this one evening, copied from the place''s list at the
     moment they were picked. A copy and not a position into contact_person: that list is
     edited and shortened over the years, so a stored position would quietly come to mean
     somebody else. No row means nobody was named — with one contact at the place there is
     nothing to name, and with several the page asks instead of guessing.';

comment on column event_contact.email is
    'Not optional, for the same reason as at the place: the page turns it into a mail. It is
     also what tells the copy and the place''s entry apart as the same person.';
