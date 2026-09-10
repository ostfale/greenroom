package de.ostfale.greenroom;

import de.ostfale.greenroom.application.port.out.ReadBackupLog;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A backup log that answers from memory instead of from disk. No test writes a file to
 * find out what a line means, and none depends on a machine that happens to run cron.
 */
public class FakeBackupLog implements ReadBackupLog {

    /** The night the tests talk about, at the hour cron actually runs. */
    public static final LocalDateTime LAST_NIGHT = LocalDateTime.of(2026, 9, 10, 3, 0);

    private Entry entry;

    @Override
    public Optional<Entry> lastEntry() {
        return Optional.ofNullable(entry);
    }

    /** As if the script had written exactly this, last night. */
    public void lastSaid(String line) {
        entry = new Entry(line, LAST_NIGHT);
    }

    public void lastSaid(String line, LocalDateTime writtenAt) {
        entry = new Entry(line, writtenAt);
    }

    /** As if there were no log at all — a development machine, or a first start. */
    public void saysNothing() {
        entry = null;
    }
}
