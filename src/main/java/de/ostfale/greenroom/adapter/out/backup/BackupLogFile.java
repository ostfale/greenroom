package de.ostfale.greenroom.adapter.out.backup;

import de.ostfale.greenroom.application.port.out.ReadBackupLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The backup log as a file on disk. On the Pi cron writes it into the same folder that is
 * already mounted into the container for the warning log, so the application sees it
 * without anything else being opened up: one path, no shell, no second mount.
 *
 * <p>Where no path is configured there is nothing to read, which is the normal state of a
 * development machine. The file is one short line per night and will not grow into
 * anything, but it is read as a stream all the same — a log nobody ever rotated must not
 * be pulled into memory to find its last line.
 */
@Component
public class BackupLogFile implements ReadBackupLog {

    private static final Logger log = LoggerFactory.getLogger(BackupLogFile.class);

    private final Path file;

    public BackupLogFile(@Value("${greenroom.backup.log:}") String file) {
        this.file = file.isBlank() ? null : Path.of(file);
    }

    @Override
    public Optional<Entry> lastEntry() {
        if (file == null || !Files.isReadable(file)) {
            return Optional.empty();
        }
        try {
            LocalDateTime writtenAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                return lines.filter(line -> !line.isBlank())
                        .reduce((earlier, later) -> later)
                        .map(line -> new Entry(line, writtenAt));
            }
        } catch (IOException | UncheckedIOException e) {
            // A log that cannot be read says nothing about the backup either way, and the
            // settings page has to come up regardless.
            log.warn("BackupLogFile :: {} could not be read", file, e);
            return Optional.empty();
        }
    }
}
