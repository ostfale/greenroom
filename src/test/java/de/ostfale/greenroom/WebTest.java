package de.ostfale.greenroom;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test that goes the whole way: browser request, controller, use case, real Postgres —
 * and back as rendered HTML.
 *
 * <p>Composition rather than a base class: Spring resolves meta-annotations itself, every
 * test class keeps its single inheritance, and the three annotations are declared once.
 * All of them together form one context key, so the tests still share one context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface WebTest {
}
