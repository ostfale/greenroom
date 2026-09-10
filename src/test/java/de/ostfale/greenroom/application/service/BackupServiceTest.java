package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.FakeBackupLog;
import de.ostfale.greenroom.application.port.in.Backup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wording of the log is the contract between {@code backup.sh} and this page, so it is
 * asserted with the lines the script really writes — the two good ones, the complaint it
 * makes about its own setup, and whatever pg_dump or git leaves behind when it breaks.
 */
class BackupServiceTest {

    private final FakeBackupLog log = new FakeBackupLog();
    private BackupService backup;

    @BeforeEach
    void freshService() {
        backup = new BackupService(log);
    }

    @Test
    void aPushedLineMeansTheDumpIsAtHiDrive() {
        log.lastSaid("backup :: 2026-09-10 pushed");

        assertThat(backup.lastRun().state()).isEqualTo(Backup.State.PUSHED);
        assertThat(backup.lastRun().ranAt()).isEqualTo(FakeBackupLog.LAST_NIGHT);
    }

    /** A night without a commit is not a night without a backup — that is the whole point. */
    @Test
    void nothingChangedIsAGoodNightToo() {
        log.lastSaid("backup :: 2026-09-10 nothing changed");

        assertThat(backup.lastRun().state()).isEqualTo(Backup.State.UNCHANGED);
    }

    @Test
    void anythingTheScriptDidNotSayItselfIsAFailedRun() {
        log.lastSaid("pg_dump: error: connection to server failed");

        assertThat(backup.lastRun().state()).isEqualTo(Backup.State.FAILED);
    }

    /** The script's own complaint carries its prefix but neither good ending. */
    @Test
    void theScriptComplainingAboutItsSetupIsAFailedRunAsWell() {
        log.lastSaid("backup :: ./backup is not a git repository");

        assertThat(backup.lastRun().state()).isEqualTo(Backup.State.FAILED);
    }

    @Test
    void withoutALogNothingIsKnownAndNoTimeIsInvented() {
        log.saysNothing();

        assertThat(backup.lastRun().state()).isEqualTo(Backup.State.UNKNOWN);
        assertThat(backup.lastRun().ranAt()).isNull();
    }
}
