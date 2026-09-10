#!/bin/sh
#
# Does the backup still come back? A dump that reads cleanly is not yet a backup — this
# plays the one that is actually at HiDrive into a throwaway PostgreSQL, counts what
# arrived against the database that is running, and starts the application on top of it.
# Only the last step tells the truth: Flyway has to validate, the pages have to answer, and
# a photo has to come out of the bytea as a picture again.
#
# Nothing here touches the live stack. The check gets its own container, its own network
# and its own temporary directory, and takes them all down again when it is done, whether
# it passed or not.
#
# Run it by hand, on the Pi, beside compose.pi.yaml:
#
#     ./restore-check.sh
#
# Worth doing after every schema migration, because that is when a dump and the application
# that has to read it drift apart. A check is only ever valid for the dump it checked, so
# repeating it is the whole point — the answer does not keep.
#
# It clones from the remote of the backup repository rather than reading ./backup, because
# what matters in an emergency is what left the machine, not what stayed on it.
#
set -eu

cd "$(dirname "$0")"

DIR="${BACKUP_DIR:-./backup}"
PI_COMPOSE="${PI_COMPOSE:-compose.pi.yaml}"
PORT="${RESTORE_CHECK_PORT:-18383}"

DB=greenroom-restore-check
APP=greenroom-app-check
NET=greenroom-restore-net
PASSWORD=throwaway

WORK=""

say() {
    echo "restore-check :: $*"
}

fail() {
    say "$*"
    exit 1
}

# Whatever happens, the machine is left as it was found.
clean_up() {
    docker rm --force "$APP" "$DB" >/dev/null 2>&1 || true
    docker network rm "$NET" >/dev/null 2>&1 || true
    [ -n "$WORK" ] && rm -rf "$WORK"
    return 0
}
trap clean_up EXIT

restored() {
    docker exec --env PGPASSWORD="$PASSWORD" "$DB" psql --host 127.0.0.1 \
        --username greenroom --dbname greenroom --tuples-only --no-align --command "$1"
}

live() {
    docker compose --file "$PI_COMPOSE" exec -T db psql --username greenroom \
        --dbname greenroom --tuples-only --no-align --command "$1"
}

# --- what is at HiDrive ---------------------------------------------------------------

[ -d "$DIR/.git" ] || fail "$DIR is not a git repository — see the header of backup.sh"

REMOTE=$(git -C "$DIR" remote get-url origin)
WORK=$(mktemp -d)
DUMP="$WORK/clone/greenroom.sql"

git clone --quiet "$REMOTE" "$WORK/clone" || fail "the backup repository could not be cloned"
[ -f "$DUMP" ] && [ -s "$DUMP" ] || fail "the clone carries no dump"

THERE=$(git -C "$WORK/clone" rev-parse HEAD)
HERE=$(git -C "$DIR" rev-parse HEAD)
say "cloned $(git -C "$WORK/clone" log -1 --format=%s)"
[ "$THERE" = "$HERE" ] || say "note: the copy here is at $(echo "$HERE" | cut -c1-8), \
HiDrive at $(echo "$THERE" | cut -c1-8) — a push did not go through"

# The marker pg_dump writes once it is through. A dump that broke off has everything but
# this line, and reads like a whole one until the moment it is needed.
grep --quiet "PostgreSQL database dump complete" "$DUMP" \
    || fail "the dump has no end marker and is not whole"

TABLES=$(grep "^COPY public\." "$DUMP" | cut -d" " -f2 | cut -d. -f2)
[ -n "$TABLES" ] || fail "the dump carries no table at all"
say "the dump is whole and holds $(echo "$TABLES" | wc -l | tr -d " ") tables"

# --- into a database of its own ---------------------------------------------------------

docker rm --force "$DB" >/dev/null 2>&1 || true
docker run --detach --name "$DB" \
    --env POSTGRES_USER=greenroom \
    --env POSTGRES_PASSWORD="$PASSWORD" \
    --env POSTGRES_DB=greenroom \
    postgres:17-alpine >/dev/null

# Asked over TCP and against the database itself, not with pg_isready: while the image is
# still setting itself up it runs a server on the unix socket that answers "ready" and does
# not have the database yet, and a restore started there fails on the very first line.
waited=0
until restored "select 1" >/dev/null 2>&1; do
    waited=$((waited + 1))
    [ "$waited" -lt 60 ] || fail "the throwaway database did not come up"
    sleep 1
done

# Every error, not only the first: one broken statement in the middle is what a restore
# looks like when it half works, and half a database is worse than none.
if ! docker exec --interactive --env PGPASSWORD="$PASSWORD" "$DB" psql --host 127.0.0.1 \
        --username greenroom --dbname greenroom --quiet \
        < "$DUMP" > "$WORK/restore.out" 2> "$WORK/restore.err"; then
    say "psql refused the dump:"
    head -c 2000 "$WORK/restore.err"
    exit 1
fi
if [ -s "$WORK/restore.err" ]; then
    say "the restore complained:"
    head -c 2000 "$WORK/restore.err"
    exit 1
fi
say "the dump went in without a single error"

# --- the same rows as the machine that is running ----------------------------------------

if docker compose --file "$PI_COMPOSE" ps --quiet db >/dev/null 2>&1 \
        && [ -n "$(docker compose --file "$PI_COMPOSE" ps --quiet db)" ]; then
    wrong=0
    for table in $TABLES; do
        here=$(restored "select count(*) from public.$table")
        there=$(live "select count(*) from public.$table" | tr -d "\r")
        if [ "$here" = "$there" ]; then
            printf "  %-24s %s\n" "$table" "$here"
        else
            printf "  %-24s restored=%s live=%s  MISMATCH\n" "$table" "$here" "$there"
            wrong=$((wrong + 1))
        fi
    done
    [ "$wrong" -eq 0 ] || fail "$wrong table(s) came back with a different number of rows"
    say "every table came back with the rows the running database has"
else
    say "note: nothing is running here to compare against — rows counted, not checked"
    for table in $TABLES; do
        printf "  %-24s %s\n" "$table" "$(restored "select count(*) from public.$table")"
    done
fi

# --- and the application on top of it ------------------------------------------------------

IMAGE=$(docker inspect --format "{{.Config.Image}}" \
    "$(docker compose --file "$PI_COMPOSE" ps --quiet app 2>/dev/null)" 2>/dev/null || true)
[ -n "$IMAGE" ] || IMAGE=ghcr.io/ostfale/greenroom:latest

docker network create "$NET" >/dev/null 2>&1 || true
docker network connect "$NET" "$DB"
docker rm --force "$APP" >/dev/null 2>&1 || true
docker run --detach --name "$APP" --network "$NET" \
    --publish "$PORT:8383" \
    --env SPRING_DATASOURCE_URL="jdbc:postgresql://$DB:5432/greenroom" \
    --env SPRING_DATASOURCE_USERNAME=greenroom \
    --env SPRING_DATASOURCE_PASSWORD="$PASSWORD" \
    --env SPRING_DOCKER_COMPOSE_ENABLED=false \
    "$IMAGE" >/dev/null
say "started $IMAGE against the restored database"

waited=0
until [ "$(curl --silent --output /dev/null --write-out "%{http_code}" \
        "http://localhost:$PORT/" || true)" = "200" ]; do
    waited=$((waited + 1))
    if [ "$waited" -ge 90 ]; then
        say "the application never answered. Its last words:"
        docker logs "$APP" 2>&1 | tail -20
        exit 1
    fi
    sleep 1
done

# Flyway is the one that has to agree: the dump brings the schema history with it, so an
# up-to-date schema means the restore is a database this jar can go on working with.
docker logs "$APP" 2>&1 | grep --quiet "No migration necessary" \
    || fail "Flyway did not find the schema up to date — the dump and this jar disagree"

for page in / /event /event/past /location /speaker /note /settings; do
    code=$(curl --silent --output /dev/null --write-out "%{http_code}" "http://localhost:$PORT$page")
    [ "$code" = "200" ] || fail "$page answered $code"
    printf "  %-16s %s\n" "$page" "$code"
done

# The one place a plain-text dump can quietly lose something: a photo travels as an escaped
# byte string and comes back a picture, or it does not come back at all.
SPEAKER=$(restored "select speaker_id from public.speaker_photo limit 1")
if [ -n "$SPEAKER" ]; then
    type=$(curl --silent --output "$WORK/photo.bin" --write-out "%{content_type}" \
        "http://localhost:$PORT/speaker/$SPEAKER/photo")
    case "$type" in
        image/*) say "a stored photo came back as $type, $(wc -c < "$WORK/photo.bin" | tr -d " ") bytes" ;;
        *) fail "a stored photo came back as $type — the images did not survive" ;;
    esac
fi

say "the backup of $(git -C "$WORK/clone" log -1 --format=%ad --date=short) restores and runs"
