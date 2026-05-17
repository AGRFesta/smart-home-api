plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.pitest)
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

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation(libs.arrow.core)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.jackson)

    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation("org.springframework:spring-context")
    testFixturesImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testFixturesImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testFixturesImplementation(libs.ktor.client.mock)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.kotest.assertions.json)

    testImplementation(project(":core"))
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
}

pitest {
    pitestVersion = libs.versions.pitest.engine.get()
    junit5PluginVersion = libs.versions.pitest.junit5.get()
    targetClasses = listOf("org.agrfesta.sh.api.providers.*")
    excludedClasses = listOf("org.agrfesta.sh.*Test*", "org.agrfesta.sh.*Fixtures*")
    avoidCallsTo = setOf("kotlin.jvm.internal")
    mutators = listOf("STRONGER")
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
    threads = 4
    useClasspathFile = true
    mutationThreshold = 43
}

tasks.withType<Test> {
    useJUnitPlatform()
}
