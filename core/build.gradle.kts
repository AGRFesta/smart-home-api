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
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation(libs.arrow.core)

    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation("org.springframework:spring-context")
    testFixturesImplementation(libs.arrow.core)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
    testFixturesImplementation(libs.kotest.property)

    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.kotest.property)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") // runBlocking for property tests
    testImplementation(libs.mockk)
    testImplementation(libs.archunit.junit5)
}

pitest {
    pitestVersion = libs.versions.pitest.engine.get()
    junit5PluginVersion = libs.versions.pitest.junit5.get()
    targetClasses = listOf("org.agrfesta.sh.*")
    excludedClasses = listOf("org.agrfesta.sh.*Test*", "org.agrfesta.sh.*Fixtures*")
    avoidCallsTo = setOf("kotlin.jvm.internal")
    mutators = listOf("STRONGER")
    excludedGroups = listOf("pbt") // property-based tests are excluded from mutation analysis (see issue.md)
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
    threads = 4
    useClasspathFile = true
    mutationThreshold = 70
}

tasks.withType<Test> {
    useJUnitPlatform()
}
