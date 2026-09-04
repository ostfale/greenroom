package de.ostfale.greenroom.adapter.out.geo;

import com.sun.net.httpserver.HttpServer;
import de.ostfale.greenroom.application.port.out.LookUpAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.ostfale.greenroom.Fixtures.anAddress;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one adapter that talks to somebody outside, tested against a server of its own. Not
 * against OpenStreetMap: a test that asks a public service is a test that fails on a train,
 * and asking it on every build is the usage policy broken.
 *
 * <p>A real server rather than a mocked client, because what is worth testing here is the
 * transport — that a deadline exists, and that everything which can go wrong on a wire
 * comes out as the same empty answer.
 */
class NominatimLookupTest {

    private static final String WHO_WE_ARE = "greenroom test (test@example.org)";

    private HttpServer service;
    private String baseUrl;

    /** What the service was asked, so the usage policy can be checked from the outside. */
    private final AtomicReference<String> question = new AtomicReference<>();
    private final AtomicReference<String> caller = new AtomicReference<>();

    @BeforeEach
    void startAServiceOfOurOwn() throws IOException {
        // Port zero: the machine picks one that is free, so two builds never collide.
        service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.start();
        baseUrl = "http://127.0.0.1:" + service.getAddress().getPort();
    }

    @AfterEach
    void stopIt() {
        service.stop(0);
    }

    @Test
    void anAddressThatIsFoundComesBackAsAPosition() {
        answering(200, "[{\"lat\":\"53.5511\",\"lon\":\"9.9937\"}]");

        assertThat(lookup().find(anAddress())).hasValueSatisfying(where -> {
            assertThat(where.latitude()).isEqualTo(53.5511);
            assertThat(where.longitude()).isEqualTo(9.9937);
        });
    }

    @Test
    void anAddressNobodyCanPlaceIsAnEmptyAnswer() {
        answering(200, "[]");

        assertThat(lookup().find(anAddress())).isEmpty();
    }

    @Test
    void anAnswerWithoutAPointInItIsAnEmptyAnswer() {
        answering(200, "[{\"display_name\":\"Hamburg\"}]");

        assertThat(lookup().find(anAddress())).isEmpty();
    }

    @Test
    void aServiceThatIsBrokenIsAnEmptyAnswer() {
        answering(500, "");

        assertThat(lookup().find(anAddress())).isEmpty();
    }

    /**
     * The reason this adapter carries deadlines. The lookup runs on the thread that has to
     * render the page afterwards, and a service which simply stops answering throws
     * nothing: without a deadline that thread waits for as long as the other side keeps
     * the socket open, and no catch ever runs.
     */
    @Test
    void aServiceThatDoesNotAnswerDoesNotHoldThePage() throws Exception {
        CountDownLatch letItAnswer = new CountDownLatch(1);
        service.createContext("/search", exchange -> {
            try {
                letItAnswer.await(10, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException alreadyGone) {
                // The caller gave up long before this, which is what the test is about.
            }
            exchange.close();
        });

        long before = System.nanoTime();
        var answer = lookupWithAShortDeadline().find(anAddress());
        Duration waited = Duration.ofNanos(System.nanoTime() - before);
        letItAnswer.countDown();

        assertThat(answer).isEmpty();
        assertThat(waited).isLessThan(Duration.ofSeconds(5));
    }

    /**
     * OpenStreetMap asks callers to say who they are and to take one answer at a time.
     * Breaking that is what gets an address blocked, so it is worth pinning down.
     */
    @Test
    void theCallSaysWhoIsAskingAndAsksForOneAnswer() {
        answering(200, "[]");

        lookup().find(anAddress());

        assertThat(caller.get()).isEqualTo(WHO_WE_ARE);
        assertThat(question.get()).contains("format=jsonv2").contains("limit=1");
        assertThat(URLDecoder.decode(question.get(), StandardCharsets.UTF_8))
                .contains("Musterweg 1, 22179 Hamburg");
    }

    /** From now on the service answers that, and remembers what it was asked. */
    private void answering(int status, String body) {
        service.createContext("/search", exchange -> {
            question.set(exchange.getRequestURI().getQuery());
            caller.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream answer = exchange.getResponseBody()) {
                answer.write(bytes);
            }
        });
    }

    private LookUpAddress lookup() {
        return new NominatimLookup(baseUrl, WHO_WE_ARE,
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    /** Short enough that the test does not sit out the real deadline. */
    private LookUpAddress lookupWithAShortDeadline() {
        return new NominatimLookup(baseUrl, WHO_WE_ARE,
                Duration.ofMillis(200), Duration.ofMillis(200));
    }
}
