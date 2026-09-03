# greenroom

Planning tool for the Java User Group Hamburg. Single user, runs in a container on a
Raspberry Pi 5 in a home network. No authentication by design.

It replaces an Obsidian vault. Every feature has to earn its place by making the planning
of an evening easier than a Markdown note would — that is the only benchmark.

## Principles

- **KISS.** One user, one machine, a few hundred rows. Do not build for scale, for
  multi-tenancy or for a persistence swap that will never happen.
- **DRY.** One field list per concept. No parallel model that exists only to be mapped.
  The one command record is `PastEvening`, and it earns its place: its fields belong to
  four different records, none of which is stored yet when the form is submitted.
- **What is copied is not referenced.** Whatever an evening was announced with must stay
  what it was, however the underlying record changes later.
- **What is a matter of judgement is shown, not refused.** A clash, a step the state
  machine allows, a venue we gave up — the page says so, somebody decides. Refuse only what
  would be nonsense.
- **The tool holds what happened, it does not act.** No mail is sent from here and no line
  is written by itself: the page opens the mail client with an address, and the history is
  what somebody typed. An automatism that has to be understood before it is trusted costs
  more than it saves.
- When a rule here and a simpler solution collide, say so instead of following the rule
  silently.

## Language

- Code, identifiers, comments, commit messages, log messages: English.
- UI texts and Thymeleaf templates: German. The German lives in the template — a service
  never builds a sentence.
- A refusal is a name, not a sentence. The records throw `RuleViolated(Rule.X)`, and the
  German for it stands in `messages.properties` under `rule.X`. The web adapter looks it
  up in one place, `ErrorMessages`, so no controller decides what a refusal means and no
  code reads an exception message back. `MessagesTest` fails on a rule without a text and
  on a text without a rule; the ArchUnit rule `domainRefusesByName` keeps the domain from
  going back to prose. The services keep their own `IllegalArgumentException` guards —
  "already stored" is a programming error, not something a page explains.
- Talk to me in German.
- Commit messages: a subject line, then dashed bullets. No prose paragraphs — one point
  per bullet, and only what a reader of the diff cannot see for themselves.

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
`V1__schema.sql` changed, turns the Thymeleaf cache off and serves the static files from
the source tree. Never activate it there.

## Architecture: ports and adapters

    de.ostfale.greenroom
    ├── domain        aggregates, value objects, state transitions
    ├── application   port.in, port.out, service (use cases, @Transactional)
    ├── adapter       in.web, out.image, out.geo
    └── config

The hexagon is about direction of dependency, not about purity:

- The domain classes **are** the persistence model. They carry Spring Data mapping
  annotations (`@Id`, `@Table`, `@Column`, `@MappedCollection`) directly. No second set of
  records in the adapter, no mappers.
- What the domain must stay free of is framework *behaviour*: no `@Controller`,
  no `@Service`, no `@Transactional`, nothing from `org.springframework.web` or Thymeleaf.
  State transitions and invariants are plain Java and testable without a context.
- There is no `out.persistence`. The outgoing ports are Spring Data interfaces and Spring
  Data implements them; a hand-written adapter would hold nothing but delegation. The
  consequence is that swapping the database means rewriting the ports, not an adapter —
  accepted knowingly, because that swap is not going to happen.
- `outgoingPortsAreInterfaces` is about the ports themselves. A record that crosses a port
  and the failure a port declares live in `port.out` too and are not ports.
- ArchUnit enforces this in `ArchitectureTest`. The rules encode decisions, so a failing
  rule usually means the design drifted — fix the design. Changing a rule is allowed when
  the *decision* changed, and then only together with a note here.

## Ubiquitous language

Use these names — they come from the domain, not from the framework:

- `Event` — one evening. Never call it "Meetup": that word means meetup.com here.
- `Talk` — one presentation inside an Event (1..n).
- `motto` — optional name for an evening, used when it carries several talks.
- `Activity` — one line of the history: a mail went out, or one came back.
- `Note` — a slip in the box: an idea with a stamp, belonging to nothing.
- `NextStep` — the one thing an evening is waiting for. Read off the record, not stored,
  and asked for by the overview alone. Not a second state machine beside `EventStatus`:
  that one says how far the planning has come, this one what the next hand has to do.
- `Speaker`, `Location`, `ContactPerson`, `Tag`

The `Event` has no title. Its display name is the `motto` if one is set, otherwise the
title of its single talk. With one talk nothing is maintained twice; with several the
evening gets a name of its own.

In the German UI an `Event` is called "Event", not "Abend", and a `Tag` is called "Tag",
not "Schlagwort" — one concept, one word, and it is the one the domain uses.

Everything in the source tree is English: package names, class names, enum constants,
method names, table and column names, migration file names. German appears only in
UI texts, in Thymeleaf templates and in the data itself.

## Domain model

Shape:

- an `Event` has at least one `Talk`, and a `Talk` at least one `Speaker` — from the moment
  it is created, in every state. A talk is found by approaching a person, so it comes into
  being with its speaker. There is no topic without a person
- an `Event` has exactly one `Location`, a `Location` at least one `ContactPerson`
- a `ContactPerson` and a `Speaker` each have at least an email address
- a `Talk` has no duration, and an `Event` carries a `moderator` as a name and nothing
  else — not a reference to anybody
- a `Talk` carries the hour it begins at, and the `Event` does not: with one talk the
  evening starts when it does, with three they start one after another. `Event.startsAt()`
  is the earliest of them, derived and never stored. A new talk begins at `Talk.USUALLY`,
  which is the hour a JUG evening begins at; the field may be emptied, for the years
  nobody wrote a time down

Copied, not referenced:

- the announced biography is copied onto the `Talk` when the speaker is put on it and is
  edited there; rewriting a `Speaker` bio leaves earlier evenings untouched
- an `Event` stores the tag words it was given, not a reference to the list in the settings
- a `Location` keeps every address it ever had; only the active flag moves. `capacity` sits
  on the `Address`, because the seat count of an old address is part of what that evening
  was
- the position sits on the `Address`, for the reason the seat count does: an old address
  points at where that evening was. It is looked up once from the written address and kept;
  where nobody can place it there is none, and the page shows no map. Not being found is a
  property of a thin address, never a reason to refuse writing it down
- `Location.inUse` is not `Address.active`. The address flag says where they are now, this
  one whether we still go there at all. A place we gave up keeps its evenings, its
  addresses and its contacts; it is only no longer offered when an evening looks for a
  venue — unless that evening already sits there. It reads as "Aktiv" on the page: the
  German word for the address flag would be the same one, which is why the field is not
  called `active`

Asking:

- the speaker is asked about the date first, and only once everybody has said yes are the
  venues asked, one after another. That order is how the evening is planned, not something
  the tool tracks: it knows no inquiry and no outcome
- writing is done in the mail client. The page carries the address — the speakers of the
  evening, the contacts at the venue — and a `mailto:` link opens it. What is written
  there is nobody's business here, and no draft is composed for it
- what came of it is one line in the history, typed by hand

History:

- an `Activity` is a mail that went out or one that came back, on a day, in whatever words
  describe it. Nothing else: a state the evening already carries is not written here a
  second time, and a thought that is not an event of the evening is a `Note`
- nothing appends a line by itself. The history is exactly what somebody typed
- an `Activity` is never edited or deleted. The record has no `with…` method and its port
  declares no way to; the only deletion is the cascade when the evening goes
- a `Note` is the opposite and points at nothing: it records what was thought, not what
  happened, so it may be changed and thrown away. Its stamp says when it was written and
  does not move when it is put right

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

- Full page and fragment share one route, the fragment selected by `headers = "HX-Request"`
  on its own mapping — no extra library.
- Fragments live in `templates/fragments` and are named after what they replace.
- No JavaScript framework, no inline script blocks beyond a few lines. A control the
  browser does not have — a dropdown with several choices, a form that folds away — is a
  `details` with checkboxes, not a library.
