# greenroom

Planning tool for the Java User Group Hamburg. Single user, runs in a container on a Raspberry Pi 5 in a home network.
No authentication by design.

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
- ArchUnit rules live in `ArchitectureTest`; fix the design, not the rule

## Architecture: ports and adapters

    de.ostfale.greenroom
    ├── domain        aggregates, value objects, state transitions
    ├── application   port.in, port.out, service (use cases, @Transactional)
    ├── adapter       in.web, in.scheduling, in.importer,
    │                 out.persistence, out.mail, out.geo
    └── config


## Ubiquitous language

Use these names — they come from the domain, not from the framework:

- `Event` — one evening. Never call it "Meetup": that word means meetup.com here.
- `Talk` — one presentation inside an Event (0..n; game nights have none).
- `Idea` — a topic before it has a date.
- `SpeakerInquiry`, `VenueInquiry` — a request that was sent, with an outcome.
- `Speaker`, `Location`, `ContactPerson`, `Tag`, `Activity`, `PlanningSettings`
- `Activity` is append-only: entries are never edited or deleted.
- Domain events are named after what happened: `SpeakerConfirmed`, `VenueConfirmed`.

## Database

- Migrations in `src/main/resources/db/migration`, named `V<n>__snake_case.sql`.
- Never edit an applied migration. Add a new one.
- Tables and columns snake_case, table names singular.
- Event dates are `date`, not timestamps. Application timezone is Europe/Berlin.


## Tests

- Persistence tests run against real Postgres via Testcontainers. Never H2.
- Web tests use MockMvc plus jsoup to assert the rendered fragment.
- A bug fix starts with a failing test.
- no pure mocks create configurable fakes for ports

## htmx

- Full page and fragment share one route; the fragment handler carries `@HxRequest`.
- Fragments live in `templates/fragments` and are named after what they replace.
- No JavaScript framework, no inline script blocks beyond a few lines.
