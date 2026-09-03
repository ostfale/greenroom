package de.ostfale.greenroom.application.port.in;

import java.time.LocalDate;

/**
 * The overview the tool opens with. One method, because the page is one question: what is
 * the state of things right now.
 */
public interface ShowDashboard {

    /**
     * Everything the overview shows.
     *
     * @param today the day to measure against — whether an evening is still ahead is a
     *              question about a day, and the caller says which
     */
    Dashboard asOf(LocalDate today);
}
