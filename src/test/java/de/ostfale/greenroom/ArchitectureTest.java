package de.ostfale.greenroom;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

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

    @ArchTest
    static final ArchRule adaptersAreIsolated = noClasses()
            .that().resideInAPackage("..adapter.(*)..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.(*)..")
            .because("adapters talk through the application layer, never to each other");

    // --- the domain stays plain Java ----------------------------------------------

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..greenroom.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax..",
                    "com.fasterxml.jackson..",
                    "org.slf4j..")
            .because("the domain must survive a change of framework, persistence and UI");

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

    @ArchTest
    static final ArchRule persistenceStaysInItsAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.data.jdbc..",
                    "org.springframework.data.relational..")
            .because("Spring Data types must not leak past the persistence adapter");

    @ArchTest
    static final ArchRule outgoingPortsAreInterfaces = com.tngtech.archunit.lang.syntax
            .ArchRuleDefinition.classes()
            .that().resideInAPackage("..application.port.out..")
            .should().beInterfaces();

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
