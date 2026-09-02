package de.ostfale.greenroom;

import de.ostfale.greenroom.application.port.out.EventRepository;
import de.ostfale.greenroom.application.port.out.LocationRepository;
import de.ostfale.greenroom.application.port.out.NoteRepository;
import de.ostfale.greenroom.application.port.out.SpeakerRepository;
import de.ostfale.greenroom.application.port.out.TagRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for every test that needs a database. Same major version as production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    /** Available wherever this configuration is imported, which is every test with a database. */
    @Bean
    public TestDatabase testDatabase(EventRepository events, SpeakerRepository speakers,
                                     LocationRepository locations, TagRepository tags,
                                     NoteRepository notes) {
        return new TestDatabase(events, speakers, locations, tags, notes);
    }
}
