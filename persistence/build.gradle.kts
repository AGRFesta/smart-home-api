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
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
