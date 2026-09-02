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

An inquiry can be sent from the application or handed to the local mail client; both write
the inquiry down. Sending needs a host, and without one nothing goes out — the mail is
written to the log instead, which is the state in development and in the tests. On the Pi:

    SPRING_MAIL_HOST=smtp.example.org
    SPRING_MAIL_USERNAME=info@example.org
    SPRING_MAIL_PASSWORD=...
    GREENROOM_MAIL_FROM=info@example.org

Port 465 with SSL is the default; 587 with STARTTLS works as a relay. Every mail carries a
blind copy to the sending address, because a mail sent this way never reaches the sent
folder of the mailbox.

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

The backup of the database off the machine is still to be written.
