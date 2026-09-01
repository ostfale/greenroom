package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.locations.Address;
import de.ostfale.greenroom.domain.locations.ContactPerson;
import de.ostfale.greenroom.domain.locations.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LocationService implements ManageLocations {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        log.debug("LocationService :: init");
        this.locationRepository = locationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Location> all() {
        var foundLocations = locationRepository.findAllByOrderByNameAsc();
        log.debug("LocationService :: all locations {}", foundLocations);
        return foundLocations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Location> matching(String fragment) {
        return fragment == null || fragment.isBlank() ? all() : locationRepository.search(fragment.strip());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Location> byId(Long id) {
        return id == null ? Optional.empty() : locationRepository.findById(id);
    }

    @Override
    public Location change(Location location) {
        if (location.id() == null) {
            throw new IllegalArgumentException("LocationService :: this location was never stored");
        }
        log.debug("LocationService :: change location {}", location.id());
        return locationRepository.save(location);
    }

    @Override
    public Location addAddress(Long locationId, Address address, boolean replacesTheOthers) {
        Location location = known(locationId);
        return locationRepository.save(replacesTheOthers
                ? location.movedTo(address)
                : location.withAdditionalAddress(address));
    }

    @Override
    public Location setAddressActive(Long locationId, int position, boolean active) {
        return locationRepository.save(known(locationId).withAddressActive(position, active));
    }

    @Override
    public Location addContact(Long locationId, ContactPerson contact) {
        return locationRepository.save(known(locationId).withAdditionalContact(contact));
    }

    @Override
    public Location changeContact(Long locationId, int position, ContactPerson contact) {
        return locationRepository.save(known(locationId).withContactChanged(position, contact));
    }

    @Override
    public Location removeContact(Long locationId, int position) {
        return locationRepository.save(known(locationId).withContactRemoved(position));
    }

    private Location known(Long locationId) {
        return byId(locationId).orElseThrow(() ->
                new IllegalArgumentException("LocationService :: there is no location " + locationId));
    }

    @Override
    public Location add(Location location) {
        if (location.id() != null) {
            throw new IllegalArgumentException("LocationService :: this location is already stored");
        }
        log.debug("LocationService :: add location {}", location.name());
        return locationRepository.save(location);
    }
}
