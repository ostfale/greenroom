# greenroom — running it

How the application is built, started, deployed, watched and backed up. What it is for and
what it does is in `README.md`; the domain language and the decisions behind the design are
in `CLAUDE.md`.

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

## Deployment

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

## Observability

The application already carries the metrics: Micrometer is in the jar and the actuator
exposes `/mgmt/prometheus`. What `compose.observability.yaml` adds is everybody who reads
them — Prometheus for the numbers, a node exporter for the Pi, Loki and Grafana Alloy for
the logs, and Grafana in front of both. It is a second file over the first, so the two are
named together:

    docker compose -f compose.pi.yaml -f compose.observability.yaml pull
    docker compose -f compose.pi.yaml -f compose.observability.yaml up -d

Both files in every command, also in `down` and `logs`: a `up -d` with only the first one
takes the four extra services for leftovers. Copy `compose.observability.yaml` and the
`observability/` directory to the Pi beside `compose.pi.yaml`, put a
`GRAFANA_ADMIN_PASSWORD` in the `.env` and make the log directory once, `mkdir -p logs` —
a bind mount docker has to create itself belongs to root, and the container is user 1000.

Grafana is on port 3000, user `admin`, with Prometheus and Loki already wired up. It
brings no dashboards; the two that fit are imported by ID from grafana.com: **1860** for
the Pi and **4701** for the JVM. The logs are in Explore, and because the application's
console is JSON where Alloy collects it, they are filtered by field:
`{service="app", level="WARN"}`.

Prometheus keeps a year, Loki ninety days. Both write to a docker volume, which on the Pi
is the SSD.

Two ports are open that were not before: 3000 for Grafana and 9090 for Prometheus, whose
own page is the fastest answer to whether a target is being scraped at all. The node
exporter runs in the host's network namespace — otherwise it would measure the container
instead of the Pi — and answers on 9100 without asking who is calling. On the home network
that is the same trust the application is built on. Off the internet, all of it.

### The warning log

Beside all that, the application writes its warnings to a file: `logs/greenroom.log` in
the directory it was started from on the Pi, rolled daily or at 10 MB, ninety of them and
at most 200 MB. Only `WARN` and above go there — the console keeps every level, and that
is what Loki gets. The file is the one that is still there when nothing is collecting
anything, and it is small enough to read with `less`.

It exists only where `LOGGING_FILE_NAME` names it, which `compose.pi.yaml` does and a
build does not: `mvn verify` and `mvn spring-boot:run` write no file.

## Backup

`backup.sh` dumps the database, commits what changed and pushes it to a bare Git repository
at HiDrive over SSH — the history is the retention, and restoring is `psql < greenroom.sql`
against an empty database, with no version-matched tooling and no key. Two dumps of
unchanged data differ only in the random token PostgreSQL writes into the restore guards,
which is why the comparison leaves those two lines out: a night in which nothing happened
leaves no commit.

Set up once beside `compose.pi.yaml`, then run it nightly from cron. The header of the
script has both. The repository must not sit in an end-to-end encrypted folder — a bare
repository has to be readable by the server to be one.
