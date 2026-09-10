package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.Backup;
import de.ostfale.greenroom.application.port.in.ShowBackup;
import de.ostfale.greenroom.application.port.out.ReadBackupLog;
import org.springframework.stereotype.Service;

/**
 * Reads the last line of the backup log and says what it means.
 *
 * <p>No transaction and no database: this touches a file the script wrote. What the two
 * sides agreed on is the wording of that line, and knowing it here rather than in the
 * adapter keeps it testable without a file system.
 */
@Service
public class BackupService implements ShowBackup {

    /** How every line of the script's own begins. */
    private static final String SPOKEN = "backup :: ";

    /** The two endings of a good night, exactly as {@code backup.sh} writes them. */
    private static final String PUSHED = "pushed";
    private static final String UNCHANGED = "nothing changed";

    private final ReadBackupLog log;

    public BackupService(ReadBackupLog log) {
        this.log = log;
    }

    @Override
    public Backup lastRun() {
        return log.lastEntry()
                .map(entry -> new Backup(stateOf(entry.line()), entry.writtenAt()))
                .orElseGet(Backup::unknown);
    }

    /**
     * Anything the script did not say itself is what broke: {@code set -e} ends it before
     * either good word is written, so the last line is then the error of pg_dump, of git,
     * or of the script complaining about its own setup.
     */
    private static Backup.State stateOf(String line) {
        String said = line.strip();
        if (said.startsWith(SPOKEN) && said.endsWith(PUSHED)) {
            return Backup.State.PUSHED;
        }
        if (said.startsWith(SPOKEN) && said.endsWith(UNCHANGED)) {
            return Backup.State.UNCHANGED;
        }
        return Backup.State.FAILED;
    }
}
