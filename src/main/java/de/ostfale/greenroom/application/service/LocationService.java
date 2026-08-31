package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public Location add(Location location) {
        if (location.id() != null) {
            throw new IllegalArgumentException("LocationService :: this location is already stored");
        }
        log.debug("LocationService :: add location {}", location.name());
        return locationRepository.save(location);
    }
}
