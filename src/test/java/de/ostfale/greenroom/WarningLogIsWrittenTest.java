package de.ostfale.greenroom;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The warning log on the Pi: {@code LOGGING_FILE_NAME} names a file, and only warnings go
 * into it while the console keeps every level for Loki.
 *
 * <p>Three parts have to agree for that, and none of them is visible from the others: the
 * thresholds and the rolling policy in {@code application.yml}, the file appender Spring
 * Boot assembles from them, and the absence of a {@code logback-spring.xml} — an own
 * Logback configuration replaces Boot's and silently drops the file, with no error to see.
 * That is what happened, and this test fails when it happens again.
 */
@SpringBootTest(properties = "logging.file.name=target/warning-log/greenroom.log")
@Import(TestcontainersConfiguration.class)
class WarningLogIsWrittenTest {

    private static final Logger log = LoggerFactory.getLogger(WarningLogIsWrittenTest.class);

    @Test
    void theFileTakesTheWarningsAndNothingBelow() throws Exception {
        var beneath = "info-" + UUID.randomUUID();
        var warning = "warn-" + UUID.randomUUID();

        log.info(beneath);
        log.warn(warning);

        var written = Files.readString(Path.of("target/warning-log/greenroom.log"));
        assertThat(written).contains(warning).doesNotContain(beneath);
    }
}
