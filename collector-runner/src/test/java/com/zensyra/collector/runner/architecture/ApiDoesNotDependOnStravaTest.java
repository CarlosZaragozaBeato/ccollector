package com.zensyra.collector.runner.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Closes the decoupling effort spanning Issue A and Issue A-2: no class in
 * {@code collector-api} may depend on any class in {@code collector-strava},
 * directly or transitively, through any path (field, method signature,
 * constructor, annotation, etc).
 *
 * <p>This is the permanent regression guard, not the mechanism that first
 * proved the decoupling — that was {@code collector-api}'s own
 * {@code clean compile} succeeding without {@code collector-strava} in its
 * {@code pom.xml}. This test exists for the case that matters afterward:
 * someone re-adding the Maven dependency, or worse, someone adding a single
 * import of a {@code collector-strava} class to {@code collector-api}
 * without restoring the dependency declaration at all (which would fail to
 * compile, but ArchUnit catches the same intent even earlier, with a
 * clearer failure message naming the offending class and the path).
 *
 * <p>Lives in {@code collector-runner} because it is the only module whose
 * test classpath contains the compiled classes of every other module —
 * {@code collector-api} alone cannot see {@code collector-strava}'s classes
 * to scan them, which is exactly the property this test verifies holds.
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
}
