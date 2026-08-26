// Shared test fixtures (Testcontainers base classes for Kafka + Postgres)
// used by every service module's integration tests. Exists so "spin up a
// 3-broker Kafka + Postgres for an integration test" is written once, not
// five times.
plugins {
    `java-library`
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:kafka")
    api("org.testcontainers:postgresql")
    api("org.testcontainers:junit-jupiter")
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.assertj.core)
}
