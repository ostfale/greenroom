package de.ostfale.greenroom.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The way out to what the nightly backup left behind. {@code backup.sh} runs beside the
 * container from cron and appends one line per night; the application reads that line and
 * nothing else. It never starts a backup and could not — an application inside a container
 * has no business starting containers.
 *
 * <p>Only the last line matters: it is what the most recent run had to say. No log at all
 * is an answer, not a failure — on a development machine nobody backs anything up, and the
 * settings page then simply says so.
 */
public interface ReadBackupLog {

    /** The last thing the script wrote. Empty when there is no log to read. */
    Optional<Entry> lastEntry();

    /**
     * One line as the script wrote it, and the moment the log was last appended to. The
     * moment comes from the file rather than from the line, because a run that broke off
     * leaves the error of whatever broke and no date of its own — and that is the run whose
     * date matters most.
     */
    record Entry(String line, LocalDateTime writtenAt) {
    }
}
