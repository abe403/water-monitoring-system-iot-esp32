plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":domain"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-streams")

    testImplementation(project(":testing-support"))
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.apache.kafka:kafka-streams-test-utils")
}
