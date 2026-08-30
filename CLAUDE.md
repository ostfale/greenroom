# greenroom

Planning tool for the Java User Group Hamburg. Single user, runs in a container on a
Raspberry Pi 5 in a home network. No authentication by design.

It replaces an Obsidian vault. Every feature has to earn its place by making the planning
of an evening easier than a Markdown note would — that is the only benchmark.

## Principles

- **KISS.** One user, one machine, a few hundred rows. Do not build for scale, for
  multi-tenancy or for a persistence swap that will never happen.
- **DRY.** One field list per concept. No parallel model that exists only to be mapped.
- When a rule here and a simpler solution collide, say so instead of following the rule
  silently.

## Language

- Code, identifiers, comments, commit messages, log messages: English.
- UI texts and Thymeleaf templates: German.
- Talk to me in German.

## Stack

- Java 25, Spring Boot 4.x, Maven
- PostgreSQL, Spring Data JDBC, Flyway — no JPA, no Hibernate
- Thymeleaf + htmx; htmx is vendored in static/vendor, no CDN, no npm, no build step
- Logback, JUnit 5, Testcontainers, ArchUnit
- No Lombok. Records and explicit constructors instead.

## Commands

- `mvn verify` — build and all tests
- `mvn spring-boot:run` — starts Postgres via compose automatically
- `docker compose up -d db` — database only

## Architecture: ports and adapters

    de.ostfale.greenroom
    ├── domain        aggregates, value objects, state transitions
    ├── application   port.in, port.out, service (use cases, @Transactional)
    ├── adapter       in.web, in.scheduling, in.importer,
    │                 out.persistence, out.mail, out.geo
    └── config

The hexagon is about direction of dependency, not about purity:

- The domain classes **are** the persistence model. They carry Spring Data mapping
  annotations (`@Id`, `@Table`, `@Column`, `@MappedCollection`) directly. No second set of
  records in the adapter, no mappers.
- What the domain must stay free of is framework *behaviour*: no `@Controller`,
  no `@Service`, no `@Transactional`, nothing from `org.springframework.web` or Thymeleaf.
  State transitions and invariants are plain Java and testable without a context.
- ArchUnit enforces this in `ArchitectureTest`. The rules encode decisions, so a failing
  rule usually means the design drifted — fix the design. Changing a rule is allowed when
  the *decision* changed, and then only together with a note here.

## Ubiquitous language

Use these names — they come from the domain, not from the framework:

- `Event` — one evening. Never call it "Meetup": that word means meetup.com here.
- `Talk` — one presentation inside an Event (1..n).
- `motto` — optional name for an evening, used when it carries several talks.
- `SpeakerInquiry`, `VenueInquiry` — a request that was sent, with an outcome.
- `Speaker`, `Location`, `ContactPerson`, `Tag`, `Activity`, `PlanningSettings`
- `Activity` is append-only: entries are never edited or deleted.
- Domain events are named after what happened: `SpeakerConfirmed`, `VenueConfirmed`.

There is **no `Idea` aggregate**. A topic that has no date yet is an `Event` in state
`DRAFT` with no date; a topic that came to nothing is `CANCELLED` without a date. There is
no `DROPPED` state either.

An `Event` has **at least one** `Talk`, and a `Talk` has **at least one** `Speaker` — from
the moment it is created, in every state. Both are invariants, not just the common case,
and they follow from how a talk is found: by reading an article, watching a video or
hearing someone speak, and then approaching that person. The talk comes into being with
its speaker. There is no topic without a person, so there is nothing to model for one.

The `Event` has no title. Its display name is the `motto` if one is set, otherwise the
title of its single talk. With one talk nothing is maintained twice; with several the
evening gets a name of its own.

There is no `EventFormat`. Whether an evening is the regular one or a special day is
read off the number of talks, not stored a second time.

Everything in the source tree is English: package names, class names, enum constants,
method names, table and column names, migration file names. German appears only in
UI texts, in Thymeleaf templates and in the data itself.

German term → name in code:

| Vortrag | Anfrage | Ort, Gastgeber | Ansprechpartner |
|---|---|---|---|
| `Talk` | `Inquiry` | `Location` | `ContactPerson` |

| Verlauf, Log | Schlagwort | Fälligkeit | Vorlaufzeit | Überbuchung | Motto |
|---|---|---|---|---|---|
| `Activity` | `Tag` | `TaskState`, `DueDateCalculator` | `LeadTime` | `OverbookingFactor` | `motto` |

## Domain model

The binding design is the artifact "greenroom Domänenmodell", Fassung 2:
https://claude.ai/code/artifact/e9bf33f6-b890-42e0-8b4c-683d18cc8a00

It carries the five aggregates, the state machine, the invariants with their requirement
ids (A1, B2, C4, …) and the port cut. Consult it before designing schema or use cases.

**Where this file deviates from the artifact, this file wins.** Two points so far:

- The artifact drops `Idea`; that stands. The artifact still shows `Talk` as `0..n`
  ("Fishbowl und Spieleabend haben keinen") — superseded by the `1..n` rule above, chosen
  for simplicity. An evening without a talk is not planned in greenroom.
- Consequently D1/D2 collapses: `PUBLISHED` requires every talk to have a title and an
  abstract. The speaker is already guaranteed by the invariant.
- The artifact's `Event` has neither `motto` nor a title; `motto` is added here. Its
  `EventFormat` is dropped.

- Due dates are **calculated** from the event date and `PlanningSettings`, never stored.
  Only the deviation is stored in `TaskState`: done, or moved.
- Lead times are global, not per event.
- Two events on the same evening are a warning in the use case, never a rejected
  invariant.

## Database

- Migrations in `src/main/resources/db/migration`, named `V<n>__snake_case.sql`.
- Never edit an applied migration. Add a new one.
- Tables and columns snake_case, table names singular.
- Event dates are `date`, not timestamps. Application timezone is Europe/Berlin.

## Tests

- Persistence tests run against real Postgres via Testcontainers. Never H2.
- Web tests use MockMvc plus jsoup to assert the rendered fragment.
- A bug fix starts with a failing test.
- No pure mocks — write configurable fakes for ports.

## htmx

- Full page and fragment share one route. The fragment handler is the same method,
  selected by `headers = "HX-Request"` on the mapping — no extra library.
- Fragments live in `templates/fragments` and are named after what they replace.
- No JavaScript framework, no inline script blocks beyond a few lines.
