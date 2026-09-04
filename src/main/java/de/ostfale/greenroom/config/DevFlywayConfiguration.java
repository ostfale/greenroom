package de.ostfale.greenroom.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * A script whose checksum no longer matches what the database recorded stops Flyway from
 * starting. On the Pi that is the right answer; a development database is worth less than
 * the time spent starting it again by hand, so here the schema is rebuilt instead.
 *
 * <p>Data entered locally is lost when that happens — but only then; unchanged scripts
 * migrate as usual. Since the schema went into use it should not happen at all: it says an
 * applied script was edited, and that is a mistake rather than a step. Flyway 10 dropped
 * {@code cleanOnValidationError}, which is why this is a bean and not a property.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevFlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DevFlywayConfiguration.class);

    @Bean
    public FlywayMigrationStrategy rebuildSchemaWhenTheScriptChanged() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException e) {
                log.warn("Migration scripts no longer match the database — dropping and rebuilding the development schema");
                flyway.clean();
                flyway.migrate();
            }
        };
    }
}
