package de.ostfale.greenroom.adapter.out.geo;

import de.ostfale.greenroom.application.port.out.LookUpAddress;
import de.ostfale.greenroom.domain.locations.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenStreetMap's own search. No key, no account — but a usage policy that has to be kept:
 * one request at a time, and an identifying user agent. This asks once per address, when
 * somebody writes it down, and never again.
 *
 * <p>Every failure is the same answer here: no position. A geocoder that cannot be reached
 * must not stop somebody from writing down where the next evening will be.
 */
public class NominatimLookup implements LookUpAddress {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RestClient client;

    /** Its own client: the transport belongs to the adapter, not to whoever wires it. */
    public NominatimLookup(String baseUrl, String userAgent) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    public Optional<Position> find(Address address) {
        String written = address.line();
        if (written.isBlank()) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> found = client.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", written)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(List.class);
            if (found == null || found.isEmpty()) {
                log.info("NominatimLookup :: nothing found for {}", written);
                return Optional.empty();
            }
            Map<String, Object> first = found.getFirst();
            return Optional.of(new Position(
                    Double.parseDouble(String.valueOf(first.get("lat"))),
                    Double.parseDouble(String.valueOf(first.get("lon")))));
        } catch (RuntimeException e) {
            // Not being able to ask is the same as not being told: no map, nothing else.
            log.warn("NominatimLookup :: could not look up {}", written, e);
            return Optional.empty();
        }
    }
}
