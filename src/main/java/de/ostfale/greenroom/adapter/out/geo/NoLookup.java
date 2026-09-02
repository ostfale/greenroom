package de.ostfale.greenroom.adapter.out.geo;

import de.ostfale.greenroom.application.port.out.LookUpAddress;
import de.ostfale.greenroom.domain.locations.Address;

import java.util.Optional;

/**
 * What runs when nobody is to be asked. Every address stays without a position and every
 * page stays without a map — which is exactly what should happen when the lookup is turned
 * off, and what must happen in the tests, where nothing may leave the machine.
 */
public class NoLookup implements LookUpAddress {

    @Override
    public Optional<Position> find(Address address) {
        return Optional.empty();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
