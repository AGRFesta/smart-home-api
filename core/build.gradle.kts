plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

// Without the Spring Boot Gradle plugin, the BOM doesn't pin kotlin.version to the project's
// Kotlin version. Force it here to prevent transitive deps (e.g. MockK) from pulling in a
// kotlin-stdlib compiled with a newer metadata version than the 1.9 compiler can read.
configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:1.9.24",
        "org.jetbrains.kotlin:kotlin-reflect:1.9.24"
    )
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation(libs.arrow.core)

    testFixturesImplementation(libs.arrow.core)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
