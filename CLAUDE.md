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
- git commit messages should be short and precise: a subject line, then dashed bullets.
  No prose paragraphs — one point per bullet, and only what a reader of the diff cannot
  see for themselves

## Stack

- Java 25, Spring Boot 4.x, Maven
- PostgreSQL, Spring Data JDBC, Flyway — no JPA, no Hibernate
- Thymeleaf + htmx; htmx is vendored in static/vendor, no CDN, no npm, no build step
- Logback, JUnit 5, Testcontainers, ArchUnit
- No Lombok. Records and explicit constructors instead.

## Commands

- `mvn verify` — build and all tests
- `mvn spring-boot:run` — starts Postgres via compose automatically, profile `dev`
- `docker compose up -d db` — database only

The `dev` profile is activated by the Boot Maven plugin, not by `application.yml`: on the
Pi the application runs without a profile. It lets Flyway drop and rebuild the schema when
`V1__schema.sql` changed, and turns the Thymeleaf cache off. Never activate it there.

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
- `adaptersAreIsolated` is a slice rule, not a package predicate. The earlier wording
  forbade every dependency between two classes under `adapter..`, two classes inside the
  same adapter included, which is not what "adapters talk through the application layer"
  means. Changed when the first shared helper appeared in `adapter.in.web`: the decision
  stayed, only its expression was wrong.

## Ubiquitous language

Use these names — they come from the domain, not from the framework:

- `Event` — one evening. Never call it "Meetup": that word means meetup.com here.
- `Talk` — one presentation inside an Event (1..n).
- `motto` — optional name for an evening, used when it carries several talks.
- `SpeakerInquiry`, `VenueInquiry` — a request that was sent, with an outcome.
- `Speaker`, `Location`, `ContactPerson`, `Tag`, `Activity`, `PlanningSettings`
- `Activity` is append-only: entries are never edited or deleted.
- Domain events are named after what happened: `SpeakerConfirmed`, `VenueConfirmed`.


An `Event` has **at least one** `Talk`, and a `Talk` has **at least one** `Speaker` — from
the moment it is created, in every state. Both are invariants, not just the common case,
and they follow from how a talk is found: by reading an article, watching a video or
hearing someone speak, and then approaching that person. The talk comes into being with
its speaker. There is no topic without a person, so there is nothing to model for one.

The `Event` has no title. Its display name is the `motto` if one is set, otherwise the
title of its single talk. With one talk nothing is maintained twice; with several the
evening gets a name of its own.

In the German UI an `Event` is called "Event", not "Abend".

Everything in the source tree is English: package names, class names, enum constants,
method names, table and column names, migration file names. German appears only in
UI texts, in Thymeleaf templates and in the data itself.

## Domain model

- Event consists of at least one talk
- each talk has at least one speaker
- An event has exactly one location
- a location has at least one contact person
- each, a contact person and a speaker have at least an email adress
- a `Tag` belongs to the Event, not to the Talk. Tags are one maintained list, edited in
  the settings — but an event stores the words it was given, not a reference to that list.
  Renaming or deleting a tag later must not rewrite what an evening was announced with,
  the same reason the speaker's biography is copied onto the talk
- a location keeps every address it ever had; only the active flag moves. An evening held
  at an old address was held there
- `capacity` sits on the `Address`, not on the `Location`: a place that moves rarely keeps
  the same room, and the seat count of an old address is part of what that evening was.
  The binding numbers are entered on meetup.com anyway; here the figure is a planning aid
- the announced biography is copied onto the `Talk` the moment the speaker is put on it,
  and is edited there from then on. It does not follow the speaker: rewriting a `Speaker`
  bio leaves every evening that was announced with the old one untouched
- an `Event` carries a `moderator`: the name of whoever leads through the evening, and
  nothing else. Not a reference to a `Speaker` or a `ContactPerson` — that person is
  usually one of us, and there is nothing further about them to plan here
- the order of asking is part of the domain: the speaker is asked about the date first,
  and only once everybody has said yes do the venues get asked, one after another. That is
  why `SpeakerInquiry` and `VenueInquiry` are separate aggregates — one asks about a date
  with the person fixed, the other asks a place with the date fixed. They share only
  `InquiryOutcome` and `ContactChannel`
- an inquiry is answered once. A second attempt after a refusal is a new inquiry, so both
  stay in the history, and `askedAbout` copies the date that was proposed. An accepted
  inquiry does not move the event on by itself — the page says so, somebody decides
- a Talk has no duration — how long somebody speaks is not planned here


## Database

- Migrations in `src/main/resources/db/migration`, named `V<n>__snake_case.sql`.
- While in development there is one script, `V1__schema.sql`, holding the whole schema.
  It is extended in place; no `V2`, `V3`, … is added. After a change the dev database is
  thrown away (`docker compose down -v`) instead of migrated.
- Once the application is in use on the Pi, this flips: never edit an applied migration,
  add a new one.
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
