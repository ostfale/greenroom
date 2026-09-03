# greenroom

[![build](https://github.com/ostfale/greenroom/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/ostfale/greenroom/actions/workflows/build.yml)

Planning tool for the Java User Group Hamburg, replacing an Obsidian vault. One user, one
machine in a home network, no authentication by design. Another user group is welcome to
run it — see **Deployment** — but it is built for one group at a time, not for many.

An evening is planned from its own page: the talks with their speakers, the date, the
venue, the tags it is announced with, and the steps that carry it from a topic to an
announced event. Along the way it records what was asked of whom and what came back —
first the speakers about a date, then the places about the room — and reads back as one
chronology. Beside that there is a slip box for ideas that have no evening yet, and the
years before this one can be entered as they were.

`CLAUDE.md` holds the domain language and the decisions behind the design.

## Stack

Java 25, Spring Boot 4, Maven. PostgreSQL with Spring Data JDBC and Flyway — no JPA, no
Hibernate. Thymeleaf with vendored htmx for the pages. JUnit 5, Testcontainers and ArchUnit
for the tests. No Lombok, no npm, no build step for the frontend.

## Building and running it

    mvn verify               # build and all tests
    mvn spring-boot:run      # starts Postgres via compose, profile dev
    docker compose up -d db  # database only

The tests bring their own PostgreSQL through Testcontainers, so nothing but Docker has to
be installed. Every push and pull request runs `mvn verify` on GitHub.

Running from the build activates the `dev` profile, which lets Flyway rebuild the schema
after `V1__schema.sql` was edited, turns the Thymeleaf cache off and serves the static
files from the source tree. While the model is still moving, that one migration script is
extended in place rather than followed by a `V2`; after editing it, throw the development
database away with `docker compose down -v`.

## Operation

The application listens on port 8383, the actuator on 8382 under `/mgmt` with `info`,
`health`, `prometheus` and `mappings` exposed.

In production it runs **without a profile**: no schema rebuild, the Thymeleaf cache on,
static files from the classpath. Flyway applies the migrations in
`src/main/resources/db/migration` at startup, and from the first real installation onwards
an applied migration is never edited — a new one is added instead.

Timezone is Europe/Berlin, set by the application rather than by the host.

### Mail

The application sends nothing and needs no mail server. Where there is somebody to write
to — the speakers of an evening, the contact people at its venue — the page carries the
address as a `mailto:` link, and the local client opens with it. What is written there is
written there; that a mail went out is one line in the history, typed by hand.

### Maps

A location page shows a small map where the address could be placed. The point is looked up
once through OpenStreetMap's search and kept with the address; the map itself is an embed
from openstreetmap.org, so the browser of whoever looks at the page fetches the tiles. It is
off unless asked for, and OSM asks callers to identify themselves:

    GREENROOM_GEO_ENABLED=true
    GREENROOM_GEO_USER_AGENT=greenroom (info@example.org)

Without it nothing is looked up and no map is shown — which is also the state in the tests.

### Deployment

Every push to `main` runs the tests and then builds a container image for `linux/amd64` and
`linux/arm64` and pushes it to `ghcr.io/ostfale/greenroom`, tagged `latest` and with the
commit. The jar is built on the runner and only copied into the image — a Raspberry Pi has
better things to do than compile Spring Boot.

The Pi pulls. Copy `compose.pi.yaml` and `.env.example` there, fill in the `.env`, and:

    docker compose -f compose.pi.yaml pull
    docker compose -f compose.pi.yaml up -d

That last step is the one GitHub cannot do: the Pi sits in a home network and nothing from
outside reaches it. Either run those two lines when a change should go live, or let
something on the Pi do it on a timer.

The image is public, so pulling it needs no login. Another group can take `compose.pi.yaml`
as it is and point `GREENROOM_IMAGE` at a build of their own — everything that is specific
to one group is either in the `.env` or in `messages.properties`.

**Keep it off the internet.** There is no authentication, by design: whoever reaches the
port may read and change everything. That is a home network decision, and forwarding the
port to the outside would turn it into the wrong one.

### Backup

`backup.sh` dumps the database, commits what changed and pushes it to a bare Git repository
at HiDrive over SSH — the history is the retention, and restoring is `psql < greenroom.sql`
against an empty database, with no version-matched tooling and no key. Two dumps of
unchanged data differ only in the random token PostgreSQL writes into the restore guards,
which is why the comparison leaves those two lines out: a night in which nothing happened
leaves no commit.

Set up once beside `compose.pi.yaml`, then run it nightly from cron. The header of the
script has both. The repository must not sit in an end-to-end encrypted folder — a bare
repository has to be readable by the server to be one.
