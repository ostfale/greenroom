package de.ostfale.greenroom.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * The evenings are in Hamburg, so the application runs in Europe/Berlin — whatever the
 * container or the host is set to. Fixed on purpose: there is nothing to configure here.
 */
@Configuration(proxyBeanMethods = false)
public class TimeZoneConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfiguration.class);

    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

    @PostConstruct
    void applyTimeZone() {
        TimeZone.setDefault(BERLIN);
        log.info("Application time zone set to {}", BERLIN.getID());
    }
}
