package de.ostfale.greenroom.application.service;

import de.ostfale.greenroom.application.port.in.ManageLocations;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.domain.location.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LocationService implements ManageLocations {

    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Location> all() {
        return locations.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Location> matching(String fragment) {
        return fragment == null || fragment.isBlank() ? all() : locations.search(fragment.strip());
    }

    @Override
    public Location add(Location location) {
        if (location.id() != null) {
            throw new IllegalArgumentException("LocationService :: this location is already stored");
        }
        return locations.save(location);
    }
}
