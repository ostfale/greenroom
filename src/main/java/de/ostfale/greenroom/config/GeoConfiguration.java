package de.ostfale.greenroom.config;

import de.ostfale.greenroom.adapter.out.geo.NoLookup;
import de.ostfale.greenroom.adapter.out.geo.NominatimLookup;
import de.ostfale.greenroom.application.port.out.LookUpAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Whether addresses are looked up at all. One bean and one decision, the same shape as the
 * mailer: a condition on each of the two classes would depend on the order they happen to
 * be found in.
 *
 * <p>Turned off means no map, nothing more. Nominatim asks for an identifying user agent,
 * so an install without one does not ask at all.
 */
@Configuration(proxyBeanMethods = false)
public class GeoConfiguration {

    @Bean
    public LookUpAddress addressLookup(@Value("${greenroom.geo.enabled:false}") boolean enabled,
                                       @Value("${greenroom.geo.url:https://nominatim.openstreetmap.org}") String url,
                                       @Value("${greenroom.geo.user-agent:}") String userAgent) {
        if (!enabled || userAgent.isBlank()) {
            return new NoLookup();
        }
        return new NominatimLookup(url, userAgent);
    }
}
