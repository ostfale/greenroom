#!/bin/sh
#
# One night's backup: dump the database, commit what changed, push it off the machine.
#
# Git rather than a pile of timestamped files: the history is the retention, the diff of a
# plain dump is readable line by line, and restoring is `psql < greenroom.sql` with no
# version-matched tooling and no key. HiDrive serves a real bare repository over SSH, so
# the push needs nothing but the key.
#
# Set up once on the Pi, beside compose.pi.yaml:
#
#     ssh-keygen -t ed25519 -C greenroom-backup     # deposit the public key at HiDrive
#     git ls-remote <user>@git.hidrive.strato.com:users/<user>/greenroom-backup.git
#     mkdir backup && git -C backup init
#     git -C backup remote add origin \
#         <user>@git.hidrive.strato.com:users/<user>/greenroom-backup.git
#     git -C backup config user.name greenroom
#     git -C backup config user.email <address>
#
# The ls-remote is the probe, and it comes before the rest because it needs none of it: an
# answer of nothing at all means the key was taken and the path holds a bare repository,
# which is what an untouched backup repository looks like. It is also where the host key is
# confirmed once — a question cron cannot answer, and an unasked one it would hang on. The
# path carries no leading slash. The identity is not optional either: a Pi that has never
# committed anything has none, and git refuses the commit in the small hours.
#
# The repository must not sit in an end-to-end encrypted folder: the server would see
# nothing but opaque blocks, and a bare repository has to be readable to be a repository.
#
# Then nightly, from cron, into a log the cron user may write — /var/log is root's:
#
#     0 3 * * *  $HOME/greenroom/backup.sh >> $HOME/greenroom/logs/backup.log 2>&1
#
set -eu

cd "$(dirname "$0")"

DIR="${BACKUP_DIR:-./backup}"
DUMP="$DIR/greenroom.sql"
FRESH="$DUMP.fresh"

if [ ! -d "$DIR/.git" ]; then
    echo "backup :: $DIR is not a git repository — see the header of this script"
    exit 1
fi

# Into a file of its own first. A dump that breaks halfway must not overwrite the last one
# that was whole, and set -e ends the script before anything is committed.
docker compose -f compose.pi.yaml exec -T db \
    pg_dump --username greenroom --no-owner --no-privileges --format=plain greenroom \
    > "$FRESH"

# Two dumps of unchanged data are not the same file: PostgreSQL writes a random token into
# the restrict lines that guard a restore. Those two lines are the only difference then, so
# they are what the comparison leaves out — what gets committed is the dump exactly as
# pg_dump wrote it, tokens and all. An empty commit every night would bury the days that
# actually held something.
same_but_for_the_tokens() {
    grep -v '^\\restrict ' "$1" | grep -v '^\\unrestrict ' > "$1.plain"
    grep -v '^\\restrict ' "$2" | grep -v '^\\unrestrict ' > "$2.plain"
    cmp -s "$1.plain" "$2.plain"
    unchanged=$?
    rm -f "$1.plain" "$2.plain"
    return $unchanged
}

if [ -f "$DUMP" ] && same_but_for_the_tokens "$DUMP" "$FRESH"; then
    rm -f "$FRESH"
    echo "backup :: $(date +%F) nothing changed"
    exit 0
fi

mv "$FRESH" "$DUMP"
git -C "$DIR" add greenroom.sql
git -C "$DIR" commit --quiet --message "greenroom $(date +%F)"

# Never with --force. Git objects are only ever added, so an ordinary push cannot lose a
# night; a forced one could lose all of them.
git -C "$DIR" push --quiet origin HEAD
echo "backup :: $(date +%F) pushed"
