# greenroom

[![build](https://github.com/ostfale/greenroom/actions/workflows/build.yml/badge.svg)](https://github.com/ostfale/greenroom/actions/workflows/build.yml)

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
tags (`/settings`) can be listed and created through the UI — every slice runs from the
Thymeleaf form through the use case down to Postgres and is covered end to end.

What is missing is the event detail page, and with it everything that moves an evening
along: status transitions, assigning a venue, adding an abstract, a motto, tags, further
talks. `Activity` and `PlanningSettings` are not built yet, and nothing can be edited or
deleted so far.
