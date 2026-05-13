plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springbootManagement)
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
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-context")
    implementation(libs.arrow.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
