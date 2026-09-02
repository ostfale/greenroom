package de.ostfale.greenroom;

import de.ostfale.greenroom.application.port.out.LookUpAddress;
import de.ostfale.greenroom.domain.locations.Address;

import java.util.Optional;

/**
 * A geocoder that answers from memory instead of asking the internet. Nothing may leave
 * the machine during a test, and a test that depends on a public service is a test that
 * fails on a train.
 */
public class FakeGeocoder implements LookUpAddress {

    /** The tower, so a wrong number is recognisable as one. */
    public static final Position HAMBURG = new Position(53.5511, 9.9937);

    private Position answer = HAMBURG;
    private boolean available = true;

    @Override
    public Optional<Position> find(Address address) {
        return Optional.ofNullable(answer);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /** As if the install never switched the lookup on. */
    public void isSwitchedOff() {
        available = false;
        answer = null;
    }

    /** From now on nobody knows where that is — a thin address, or a service that is down. */
    public void knowsNothing() {
        answer = null;
    }

    public void knows(Position where) {
        answer = where;
    }

    public void forgetEverything() {
        answer = HAMBURG;
        available = true;
    }
}
