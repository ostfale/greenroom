package de.ostfale.greenroom;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packages = "de.ostfale.greenroom",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    // --- the shape of the hexagon -------------------------------------------------

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("de.ostfale.greenroom..")
            .layer("Domain").definedBy("..greenroom.domain..")
            .layer("Application").definedBy("..greenroom.application..")
            .layer("Adapter").definedBy("..greenroom.adapter..")
            .layer("Config").definedBy("..greenroom.config..")

            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config");

    /**
     * The web adapter must not reach into persistence, the importer not into the mailer.
     * Written as slices, so that "each other" means other adapters: two classes inside the
     * same adapter are free to work together.
     */
    @ArchTest
    static final ArchRule adaptersAreIsolated = SlicesRuleDefinition.slices()
            .matching("..greenroom.adapter.(*).(*)..")
            .should().notDependOnEachOther()
            .because("adapters talk through the application layer, never to each other");

    // --- the domain carries mapping, but no framework behaviour --------------------

    /**
     * Mapping annotations are welcome in the domain: the aggregates are the persistence
     * model, and a second set of records plus mappers would buy nothing here. Everything
     * else Spring stays out, so invariants and transitions remain testable without a
     * context.
     */
    @ArchTest
    static final ArchRule domainCarriesMappingOnly = noClasses()
            .that().resideInAPackage("..greenroom.domain..")
            .should().dependOnClassesThat(
                    resideInAPackage("org.springframework..")
                            .and(not(resideInAnyPackage(
                                    "org.springframework.data.annotation..",
                                    "org.springframework.data.relational.core.mapping.."))))
            .because("the domain may be mapped, but must not be driven by the framework");

    @ArchTest
    static final ArchRule domainHasNoRepositories = noClasses()
            .that().resideInAPackage("..greenroom.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.data.repository..",
                    "org.springframework.data.jdbc..")
            .because("an aggregate does not know how it is loaded");

    @ArchTest
    static final ArchRule domainIsFreeOfTheRest = noClasses()
            .that().resideInAPackage("..greenroom.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta..",
                    "javax..",
                    "com.fasterxml.jackson..",
                    "org.thymeleaf..",
                    "org.slf4j..")
            .because("validation, serialisation, rendering and logging are adapter concerns");

    @ArchTest
    static final ArchRule domainUsesJavaTime = noClasses()
            .that().resideInAPackage("..greenroom.domain..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
            .because("dates and deadlines are java.time everywhere");

    // --- where things live --------------------------------------------------------

    @ArchTest
    static final ArchRule controllersOnlyInWebAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.in.web..")
            .should().beAnnotatedWith("org.springframework.stereotype.Controller")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");

    /**
     * Repositories are declared where the use cases ask for them and implemented by
     * Spring Data; the web, scheduling and importer adapters must not reach past the
     * application layer to talk to the database.
     */
    @ArchTest
    static final ArchRule repositoriesLiveInPortOutOrPersistence = noClasses()
            .that().resideInAPackage("..greenroom.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.data.repository..",
                    "org.springframework.data.jdbc..",
                    "javax.sql..")
            .because("driving adapters go through use cases, never to the database");

    /**
     * The rule is about the ports, not about what travels through them. A record that
     * crosses a port and the failure a port declares are values, not ports — the earlier
     * wording forbade both, which was never the decision.
     */
    @ArchTest
    static final ArchRule outgoingPortsAreInterfaces = com.tngtech.archunit.lang.syntax
            .ArchRuleDefinition.classes()
            .that().resideInAPackage("..application.port.out..")
            .and(not(describe("a value or a failure that crosses a port",
                    (JavaClass type) -> type.isRecord() || type.isAssignableTo(Throwable.class))))
            .should().beInterfaces()
            .because("an outgoing port is an interface that an adapter implements");

    // --- general hygiene ----------------------------------------------------------

    @ArchTest
    static final ArchRule noCycles = SlicesRuleDefinition.slices()
            .matching("de.ostfale.greenroom.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule noFieldInjection =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams =
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noGenericExceptions =
            GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
