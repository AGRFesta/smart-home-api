plugins {
	alias(libs.plugins.jvm) // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
	alias(libs.plugins.springboot)
	alias(libs.plugins.springbootManagement)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.palantir)
}

group = "org.agrfesta.sh"
version = "0.5.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.hibernate.orm:hibernate-core")
	implementation("org.postgresql:postgresql")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation(libs.ktor.core)
	implementation(libs.ktor.okhttp)
	implementation(libs.ktor.content.negotiation)
	implementation(libs.ktor.jackson)
	implementation(libs.flyway.core)
	implementation(libs.arrow.core)
	runtimeOnly(libs.flyway.postgress)

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(module = "junit")
		exclude(module = "junit-vintage-engine")
	}
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.junit.jupiter:junit-jupiter-api")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
	testImplementation(libs.kotest.assertions.core)
	testImplementation(libs.kotest.assertions.arrow)
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("com.redis:testcontainers-redis:2.2.2")
	testImplementation("io.rest-assured:rest-assured")
	testImplementation("io.mockk:mockk:1.13.10")
	testImplementation("com.ninja-squad:springmockk:4.0.2")
	testImplementation(libs.ktor.client.mock)
}

docker {
	name = "agrfesta/smart-home-api:${version}"
	uri("agrfesta/smart-home-api:${version}")
	tag("name", "smart-home-api")
	buildArgs(mapOf("name" to "smart-home-api"))
	copySpec.from("build").into("build")
	pull(true)
	setDockerfile(file("Dockerfile"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}
