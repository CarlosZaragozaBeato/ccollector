package com.zensyra.collector.runner.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Locks in the collector-api decoupling from collector-strava (Issue #1.3)
 * with two complementary rules:
 *
 * <ol>
 *   <li><b>Negative</b>: no class in {@code collector-api} may depend on any
 *       class in {@code collector-strava}. This is the permanent regression
 *       guard — it catches both a re-added Maven dependency and a stray import
 *       left in a class that somehow compiled (e.g. via transitive classpath),
 *       naming the offending class and the exact dependency path in the
 *       failure message.
 *   <li><b>Positive</b>: at least one class in {@code collector-api} must
 *       depend on {@code collector-query}. This confirms the legitimate
 *       replacement dependency is actually present and in use, so the negative
 *       rule cannot be trivially satisfied by deleting all of collector-api.
 * </ol>
 *
 * <p>Lives in {@code collector-runner} because it is the only module whose
 * test classpath contains the compiled classes of every other module.
 *
 * <p>Negative sanity check performed on 2026-06-29: a {@code RegressionProbe}
 * class with a {@code collector-strava} field was introduced into
 * {@code collector-api}, the test failed with "Architecture Violation … Rule
 * 'no classes … should depend on classes that reside in
 * 'com.zensyra.collector.strava..' was violated", and the test passed again
 * after the probe was removed.
 */
class ApiDoesNotDependOnStravaTest {

    @Test
    void apiPackageMustNotDependOnStravaPackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zensyra.collector");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.zensyra.collector.api..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.strava..")
                .check(importedClasses);
    }

    @Test
    void readResourcesMustDependOnQueryPackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zensyra.collector");

        // AthleteRegisterResource is the only write-path endpoint in collector-api;
        // it handles OAuth token exchange and has no query port dependency by design.
        // All other Resource classes are read resources and must use collector-query.
        ArchRuleDefinition.classes()
                .that().resideInAPackage("com.zensyra.collector.api..")
                .and().haveSimpleNameEndingWith("Resource")
                .and().doNotHaveSimpleName("AthleteRegisterResource")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.query..")
                .check(importedClasses);
    }
}
