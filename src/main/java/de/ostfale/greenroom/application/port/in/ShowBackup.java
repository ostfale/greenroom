package de.ostfale.greenroom.application.port.in;

/**
 * Whether the data is safe, and since when. The settings page asks this so that a backup
 * nobody can see does not become a backup nobody trusts.
 *
 * <p>There is no second half to this use case: nothing here starts, repeats or repairs a
 * backup. Cron does that, and the tool only reads what it wrote.
 */
public interface ShowBackup {

    /** How the last run ended and when it ran. Never null: not knowing is an answer too. */
    Backup lastRun();
}
