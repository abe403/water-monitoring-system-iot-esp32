// This module has ZERO framework dependencies on purpose. No Spring, no
// Kafka client, no JDBC driver. That is not a style preference — it is the
// property the hexagonal architecture depends on: the domain must be
// testable in milliseconds with no container, no broker, and no database.
// TestingSupportPlugin (see testing-support/) has an ArchUnit rule that
// fails the build if this module ever gains such a dependency.
plugins {
    `java-library`
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
