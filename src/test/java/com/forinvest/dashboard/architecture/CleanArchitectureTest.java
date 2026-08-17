package com.forinvest.dashboard.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Executable definition of the architecture.
 *
 * <p>These rules are the reason the layering survives contact with a deadline: an import that
 * points the wrong way fails the build here instead of being noticed in review, or not at all.
 */
@AnalyzeClasses(packages = "com.forinvest.dashboard", importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    private static final String DOMAIN = "com.forinvest.dashboard.domain..";
    private static final String APPLICATION = "com.forinvest.dashboard.application..";
    private static final String INFRASTRUCTURE = "com.forinvest.dashboard.infrastructure..";

    private static final String[] FRAMEWORK_PACKAGES = {
        "org.springframework..",
        "jakarta..",
        "javax.persistence..",
        "org.hibernate..",
        "io.swagger..",
        "com.fasterxml.jackson..",
        "org.flywaydb..",
        "org.slf4j.."
    };

    /** Dependencies point inward only. */
    @ArchTest
    static final ArchRule LAYERS_ARE_RESPECTED = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain")
            .definedBy(DOMAIN)
            .layer("Application")
            .definedBy(APPLICATION)
            .layer("Infrastructure")
            .definedBy(INFRASTRUCTURE)
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule DOMAIN_IS_INDEPENDENT = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(APPLICATION, INFRASTRUCTURE)
            .because("the domain is the innermost layer and must not know about anything around it");

    /**
     * The point of the whole exercise: business rules that can be read, tested and changed without a
     * framework in the way.
     */
    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("domain rules must be testable with plain JUnit, with no Spring, JPA or Jackson involved");

    @ArchTest
    static final ArchRule APPLICATION_IS_FRAMEWORK_FREE = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("use cases are wired by UseCaseConfig, so they need no framework annotations of their own");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_KNOW_INFRASTRUCTURE = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(INFRASTRUCTURE)
            .because("use cases depend on ports, and infrastructure supplies the adapters");

    /** JPA entities are a storage detail and must never be handed out as a model. */
    @ArchTest
    static final ArchRule ENTITIES_STAY_INSIDE_PERSISTENCE = ArchRuleDefinition.classes()
            .that()
            .haveSimpleNameEndingWith("Entity")
            .should()
            .resideInAPackage("com.forinvest.dashboard.infrastructure.persistence..")
            .andShould()
            .notBePublic()
            .allowEmptyShould(true)
            .because("entities are a persistence detail; the rest of the system speaks in domain types");

    /** Controllers orchestrate use cases; they must not reach past them to storage. */
    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_TOUCH_PERSISTENCE = ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("com.forinvest.dashboard.infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.forinvest.dashboard.infrastructure.persistence..")
            .because("the web layer must go through use cases, never straight to the database");

    @ArchTest
    static final ArchRule NO_PACKAGE_CYCLES =
            slices().matching("com.forinvest.dashboard.(*)..").should().beFreeOfCycles();
}
