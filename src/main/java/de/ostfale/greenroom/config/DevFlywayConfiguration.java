package de.ostfale.greenroom.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * While the model is still moving, {@code V1__schema.sql} is edited in place, so its
 * checksum stops matching what the development database recorded. Rebuild the schema
 * instead of refusing to start.
 *
 * <p>Data entered locally is lost when that happens — but only then; an unchanged script
 * migrates as usual. Flyway 10 dropped {@code cleanOnValidationError}, which is why this
 * is a bean and not a property.
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
