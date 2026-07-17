package com.zensyra.collector.runner.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Locks in cross-source module isolation, extending the original api↛strava
 * rule (Issue #1.3) with the symmetric strava↛suunto and suunto↛strava
 * directions added when the cross-source training load composition seam landed.
 *
 * <p>Four rules in total:
 * <ol>
 *   <li><b>api↛strava (negative)</b>: no class in {@code collector-api} may
 *       depend on any class in {@code collector-strava}.
 *   <li><b>api uses query (positive)</b>: at least one Resource in
 *       {@code collector-api} must depend on {@code collector-query}, so the
 *       negative rule cannot be satisfied by deleting all of collector-api.
 *   <li><b>strava↛suunto (negative)</b>: no class in {@code collector-strava}
 *       may depend on any class in {@code collector-suunto}.
 *   <li><b>suunto↛strava (negative)</b>: no class in {@code collector-suunto}
 *       may depend on any class in {@code collector-strava}.
 *   <li><b>api↛suunto (negative)</b>: no class in {@code collector-api} may
 *       depend on any class in {@code collector-suunto}.
 * </ol>
 *
 * <p>Lives in {@code collector-runner} because it is the only module whose
 * test classpath contains the compiled classes of every other module.
 *
 * <p>Negative sanity check for rule 1 performed on 2026-06-29: a
 * {@code RegressionProbe} class with a {@code collector-strava} field was
 * introduced into {@code collector-api}, the test failed with
 * "Architecture Violation … Rule 'no classes … should depend on classes that
 * reside in 'com.zensyra.collector.strava..' was violated", and the test
 * passed again after the probe was removed.
 */
class SourceModuleIsolationTest {

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

        // AthleteRegisterResource and SuuntoAthleteRegisterResource are the only
        // write-path endpoints in collector-api; they handle OAuth token exchange
        // and have no query port dependency by design.
        // All other Resource classes are read resources and must use collector-query.
        ArchRuleDefinition.classes()
                .that().resideInAPackage("com.zensyra.collector.api..")
                .and().haveSimpleNameEndingWith("Resource")
                .and().doNotHaveSimpleName("AthleteRegisterResource")
                .and().doNotHaveSimpleName("SuuntoAthleteRegisterResource")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.query..")
                .check(importedClasses);
    }

    @Test
    void stravaPackageMustNotDependOnSuuntoPackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zensyra.collector");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.zensyra.collector.strava..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.suunto..")
                .check(importedClasses);
    }

    @Test
    void suuntoPackageMustNotDependOnStravaPackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zensyra.collector");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.zensyra.collector.suunto..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.strava..")
                .check(importedClasses);
    }

    @Test
    void apiPackageMustNotDependOnSuuntoPackage() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.zensyra.collector");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.zensyra.collector.api..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.zensyra.collector.suunto..")
                .check(importedClasses);
    }
}
