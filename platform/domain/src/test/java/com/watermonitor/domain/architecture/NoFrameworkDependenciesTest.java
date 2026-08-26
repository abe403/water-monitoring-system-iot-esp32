package com.watermonitor.domain.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The load-bearing test for the hexagonal architecture claim in
 * docs/ARCHITECTURE.md: the domain module is unit-testable in milliseconds
 * with no container, no broker, no database, because it is physically
 * incapable of importing one. If this test ever fails, something added a
 * framework dependency to the domain — that is the bug to fix, not this
 * test.
 */
class NoFrameworkDependenciesTest {

    private static final String[] FORBIDDEN_PACKAGES = {
            "org.springframework..",
            "org.apache.kafka..",
            "java.sql..",
            "javax.sql..",
            "jakarta.persistence..",
    };

    @Test
    void domainImportsNoFrameworkOrInfrastructureCode() {
        var classes = new ClassFileImporter().importPackages("com.watermonitor.domain");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.watermonitor.domain..")
                .and().resideOutsideOfPackage("com.watermonitor.domain.architecture..")
                .should().dependOnClassesThat(resideInAnyPackage(FORBIDDEN_PACKAGES))
                // The whole point of this rule is that it currently matches
                // zero classes — the domain has no framework dependencies.
                // Without this, ArchUnit 1.3's "empty should" guard (meant
                // to catch typo'd rules that accidentally check nothing)
                // would fail a passing codebase.
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
