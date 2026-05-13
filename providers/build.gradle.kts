plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
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
    implementation(project(":core"))
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.jackson)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
