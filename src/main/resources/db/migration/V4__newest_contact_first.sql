-- A place's contacts are now kept newest first. The rows already there were appended, so
-- their order is turned around. Through negative keys, because the primary key does not
-- allow two rows to share a position even for the length of one statement.
update contact_person c
set location_key = -1 - (newest.location_key - c.location_key)
from (select location, max(location_key) as location_key
      from contact_person
      group by location) newest
where newest.location = c.location;

update contact_person
set location_key = -1 - location_key;
