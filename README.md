# greenroom

[![build](https://github.com/ostfale/greenroom/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/ostfale/greenroom/actions/workflows/build.yml)

Planning tool for the Java User Group Hamburg. It replaces an Obsidian vault: one user,
running in a container on a Raspberry Pi 5 in a home network, no authentication by design.

An `Event` is one evening. It carries at least one `Talk`, and every talk has at least one
`Speaker` — a topic without a person does not exist here. See `CLAUDE.md` for the
ubiquitous language and the architectural decisions behind it.

## Running it

    mvn spring-boot:run      # starts Postgres via compose automatically, profile dev
    docker compose up -d db  # database only
    mvn verify               # build and all tests

Every push and pull request runs `mvn verify` on GitHub. The tests bring their own
Postgres through Testcontainers, so the workflow needs nothing but Docker, which the
runner already has.

The application listens on port 8383, actuator on 8382 under `/mgmt`.

Running from the build activates the `dev` profile, which lets Flyway rebuild the schema
after `V1__schema.sql` was edited — while the model is still moving, that one script is
extended in place instead of adding migrations. In production there is no profile and no
such rebuild.

## Layout

    de.ostfale.greenroom
    ├── domain        aggregates, value objects, state transitions
    ├── application   port.in, port.out, service
    ├── adapter       in.web, in.scheduling, in.importer,
    │                 out.persistence, out.mail, out.geo
    └── config

Ports and adapters. The domain classes are the persistence model and carry Spring Data
mapping annotations, but no framework behaviour — `ArchitectureTest` enforces the cut.

## Stack

Java 25, Spring Boot 4, PostgreSQL with Spring Data JDBC and Flyway, Thymeleaf plus
vendored htmx. No JPA, no Lombok, no npm, no build step for the frontend.

## Status

Early. Events (`/event`), speakers (`/speaker`), locations (`/location`) and the list of
tags (`/settings`) are listed and created through the UI — every slice runs from the
Thymeleaf form through the use case down to Postgres and is covered end to end.

An evening is planned from its detail page: date, event name, moderator, notes, venue, its
talks with their titles and abstracts, the keywords it is announced with, and the status
transitions that carry it from a topic to an announced event. The page offers only the
steps the state machine allows and says in German what a refused one is still missing; a
second evening on a day that is already taken is pointed out, not refused. Speakers,
locations and the keyword list can be edited; a keyword can be dropped, and a speaker as
long as no talk announces them.

The announced biography is copied onto a talk the moment the speaker is put on it, and is
edited there. It does not follow the speaker afterwards: what an evening was announced with
stays, the same reason an event stores its keywords as words.

`SpeakerInquiry` records what was asked of a speaker and what came back, with the proposed
date copied onto it and the number of days it has been waiting on the page. An inquiry is
answered once; asking again after a refusal is a new inquiry and both stay.

`/event/import` enters an evening that already happened: date, form, speaker, talk and the
biography of the day, in one form and without any of the planning that led to it. The
speaker is recognised by their address, so somebody who spoke before is not written down
twice, and the evening is moved as far along as the data carries it — to `DONE` with a
venue and a full talk, and no further otherwise.

`VenueInquiry` is the second question, and the mirror image of the first: there the person
is fixed and the date is asked, here the date is fixed and the place is asked. That is why
the two are separate aggregates and why a venue inquiry refuses to exist without a date.
Whom we wrote to is copied onto the inquiry, so a contact person who leaves the company
does not rewrite who was asked back then. Places are asked one after another — the tile
names the place still being waited on and how long, but it does not refuse the next
inquiry: that stays a decision, like the clash warning and like an accepted inquiry that
moves nothing on by itself.

What is missing is `Activity` and `PlanningSettings`, and the mail, geo, importer and
scheduling adapters the layout above already names.
