# greenroom

[![build](https://github.com/ostfale/greenroom/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/ostfale/greenroom/actions/workflows/build.yml)

Planning tool for the Java User Group Hamburg, replacing an Obsidian vault. It carries one
evening at a time from a topic somebody mentioned to an announced event, and it is built
for one user on one machine in a home network. Another group is welcome to run it — see
`HELP.md`.

## What it does

- **Plans an evening** — its talks with their speakers, the date, the venue, the tags it
  is announced with, and the state that says how far the planning has come.
- **Records the chronology** — what was asked of whom and what came back: first the
  speakers about a date, then the places about the room. Every line typed by hand.
- **Writes the announcement** — the abstracts with the biographies this evening announces
  its speakers with, as one block to paste, and the evening as an `.ics` file.
- **Carries the addresses** — the speakers of an evening and the contacts at its venue as
  `mailto:` links, so the mail is written in the local client.
- **Holds the ideas** — a slip box for topics that have no evening yet.
- **Keeps the years before** — the evenings already held, entered as they were.

## The pages

| Page | What is on it |
|------|---------------|
| `/` | The overview the tool opens with: the evening that is next, what every open evening is still waiting for, the topics that have no date yet, where the evenings were held, who gave them, and the counts at the bottom |
| `/event` | The evenings, each with its talks, speakers, venue and history; `/event/past` for the ones already held |
| `/speaker` | The people who gave a talk, with photo and biography |
| `/location` | The places, every address they ever had and the people to ask there, with a map excerpt |
| `/note` | The slip box: an idea with a stamp, belonging to nothing |
| `/settings` | What is set once and used everywhere — for now the list of tags |

## Stack

- **Language and framework** — Java 25, Spring Boot 4, Maven.
- **Persistence** — PostgreSQL with Spring Data JDBC and Flyway.
- **Pages** — Thymeleaf with htmx, vendored in the source tree.
- **Tests** — JUnit 5, Testcontainers, ArchUnit.
- **Delivery** — one image for `linux/amd64` and `linux/arm64`, built on every push to
  `main`, running in a container on a Raspberry Pi 5.
- **Watching it** — Prometheus, Loki and Grafana beside the application, where the graphs
  are wanted.

## Running it

    mvn verify               # build and all tests
    mvn spring-boot:run      # starts Postgres via compose, profile dev

The tests bring their own PostgreSQL through Testcontainers, so Docker is all that has to
be installed.

## Where the rest is

- **`HELP.md`** — how it is built, run, deployed, watched and backed up.
- **`CLAUDE.md`** — the domain language and the decisions behind the design: what is
  copied and what is referenced, and what the records refuse.
