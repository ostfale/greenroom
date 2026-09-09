-- The homepage of a place. It belongs to the location and not to one of its addresses:
-- a company keeps its domain when it moves. Null for every row that is already there.
alter table location
    add column website text;
