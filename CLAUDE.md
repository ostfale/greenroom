# greenroom

Planning tool for the Java User Group Hamburg: one user, one container on a Raspberry Pi 5
in a home network, no authentication by design. `README.md` says what the application does,
`HELP.md` how it is built, run, deployed and watched. This file holds only what changes how
you work on the code.

Stack: Java 25, Spring Boot 4, Maven, Spring Data JDBC, Flyway, PostgreSQL, Thymeleaf + htmx,
Testcontainers. Package root `de.ostfale.greenroom`.

## How to work here

- Talk to me in German. Code, identifiers, comments, commit messages, log messages: English.
  UI texts: German, and the German lives in the template — a service never builds a sentence.
- Verify before you report: run the tests (the command is in `HELP.md`). `ArchitectureTest`
  and `MessagesTest` are the guards. A failing rule usually means the design drifted — fix
  the design, not the rule. A rule may change only when the *decision* changed, and then
  together with a note in this file.
- Commit directly on `main`, and only when asked. Message: a subject line, then dashed
  bullets — one point per bullet, only what a reader of the diff cannot see.
- A bug fix starts with a failing test.
- Every feature has to earn its place by making the planning of an evening easier than a
  Markdown note would. That is the only benchmark.

## No source code in the transcript

This is a rule about what reaches the terminal, and every tool is bound by it. It outranks
any session default that prefers the shell for file work or asks for wide reads.

- **Answers carry no code blocks.** Not a snippet, not a shell command to type by hand, not
  a commit message for approval, not a diff. Describe what a change does and point at the
  place: `LocationService.java:37`. Where a procedure is written down, name the file that
  holds it instead of copying the commands out of it.
- **Reading is narrow.** `Read` with `offset` and `limit`, covering the passage being
  changed. `Grep` answers with file names or counts; content mode only with a pattern narrow
  enough that the answer is a line, and no `-A`, `-B`, `-C` unless the block is the answer.
- **Commands spill no source.** No `cat`, no heredoc, no `sed -n`, no unfiltered `git diff`.
  Reduce a check to its answer: a count, a name, a status, a line number.
- **Writing is narrow too.** Small edits anchored on what is already there, never a
  whole-file rewrite — a Markdown file is no exception. Where a rewrite really is the only
  way, say so first and let me decide.

## Principles

- **KISS.** One user, one machine, a few hundred rows. No scale, no multi-tenancy, no
  persistence swap that will never happen.
- **DRY.** One field list per concept. No parallel model that exists only to be mapped.
- **What is copied is not referenced.** Whatever an evening was announced with stays what it
  was, however the underlying record changes later.

## Ubiquitous language

- `Event` — one evening. Never "Meetup": that word means meetup.com here. In the German UI
  "Event", not "Abend". An `Event` has no title; its display name is the `motto` if set,
  otherwise the title of its single talk.
- `Talk` — one presentation inside an `Event` (1..n).
- `motto` — the optional name of an evening.
- `Activity` — one line of the history: a mail went out, or one came back.
- `Note` — a slip in the box, an idea belonging to nothing.
- `NextStep` — the one thing an evening waits for. Read off the record, never stored, and not
  a second state machine beside `EventStatus`: that one says how far the planning has come,
  this one what the next hand has to do.
- `Tag` — in the German UI "Tag", not "Schlagwort".
- `Speaker`, `Location`, `ContactPerson`.

## Architecture: ports and adapters

    de.ostfale.greenroom
    ├── domain        aggregates, value objects, state transitions
    ├── application   port.in, port.out, service (use cases, @Transactional)
    ├── adapter       in.web, out.image, out.geo
    └── config

The hexagon is about direction of dependency, not about purity:

- The domain classes **are** the persistence model and carry the Spring Data mapping
  annotations. No second set of records in the adapter, no mappers.
- The domain stays free of framework *behaviour*: no `@Controller`, `@Service`,
  `@Transactional`, nothing from `org.springframework.web` or Thymeleaf.
- There is no `out.persistence`. The outgoing ports are Spring Data interfaces, so swapping
  the database means rewriting the ports — accepted knowingly. A record that crosses a port
  and the failure a port declares live in `port.out` too and are not ports.
- **A refusal is a name, not a sentence.** The records throw `RuleViolated(Rule.X)`; the
  German stands in `messages.properties` under `rule.X` and is looked up in one place,
  `ErrorMessages`. `MessagesTest` fails on a rule without a text and on a text without a
  rule; the ArchUnit rule `domainRefusesByName` keeps the domain out of prose. The services
  keep their `IllegalArgumentException` guards — "already stored" is a programming error,
  not a rule.

## Domain rules

- An `Event` has at least one `Talk`, a `Talk` at least one `Speaker` — from creation, in
  every state: there is no topic without a person. An `Event` has exactly one `Location`, a
  `Location` at least one `ContactPerson`, and a `ContactPerson` and a `Speaker` each at
  least an email address.
- A `Talk` has no duration. An `Event` carries its `moderator` as a name and nothing else.
- The `Talk` carries the hour it begins at, not the `Event`. `Event.startsAt()` is the
  earliest of them, derived and never stored. A new talk begins at `Talk.USUALLY`; the field
  may be emptied, for the years nobody wrote a time down.
- The announced biography is copied onto the `Talk` and edited there. Tag words sit on the
  `Talk`, not on the evening; `Event.tags()` is the union, derived and never stored.
- A `Location` keeps every address it ever had — only `Address.active` moves, nothing is
  rewritten or dropped. `capacity` and the position sit on the `Address`, because they are
  part of what that evening was; `capacity` is the one field on a stored address that may be
  put right afterwards, on a retired one too. An address that cannot be placed has no
  position and the page shows no map — never a reason to refuse writing it down.
- An `Event` says which of its venue's addresses it was at, by position — the one place this
  project references what it elsewhere copies. Empty means the address the place has today,
  which is what a planned evening wants.
- `Location.inUse` ("Aktiv") is not `Address.active`: the address flag says where they are
  now, this one whether we still go there at all. A place given up keeps its evenings and is
  only no longer offered when an evening looks for a venue — unless it already sits there.
- An `Activity` is never edited or deleted; the record has no `with…` method and its port
  declares no way to. The only deletion is the cascade when the evening goes. A `Note` is the
  opposite: it may be changed and thrown away, and its stamp does not move when it is.
- Nothing appends a history line by itself — the history is exactly what somebody typed. The
  tool knows no inquiry and no outcome: writing is done in the mail client, the page only
  carries the addresses as `mailto:` links, and no draft is composed here.

## Database

- Migrations in `src/main/resources/db/migration`, named `V<n>__snake_case.sql`.
- An applied migration is never edited again — the data is entered once and nowhere else. A
  schema change is a new script and carries the rows that are already there. A checksum
  mismatch means somebody edited an applied script.
- Tables and columns snake_case, table names singular. Event dates are `date`, not
  timestamps; application timezone is Europe/Berlin.

## Tests

- Persistence tests run against real Postgres via Testcontainers. Never H2.
- Web tests use MockMvc plus jsoup and assert the rendered fragment.
- No mocking framework for ports — write configurable fakes.

## htmx

- Full page and fragment share one route; the fragment is selected by `headers = "HX-Request"`
  on its own mapping — no extra library.
- Fragments live in `templates/fragments` and are named after what they replace.
- A form that writes carries `guarded` and its submit button comes out of the template
  `disabled`: the first change to a field switches it on, so a lit button means unsaved.
  `SaveButtonsTest` fails on a form that forgets it.
- htmx is vendored in `static/vendor`: no CDN, no npm, no build step.
- No JavaScript framework, no inline script blocks beyond a few lines. A control the browser
  does not have — a dropdown with several choices, a form that folds away — is a `details`
  with checkboxes, not a library.
