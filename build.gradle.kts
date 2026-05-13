plugins {
	alias(libs.plugins.jvm)
	alias(libs.plugins.springboot)
	alias(libs.plugins.springbootManagement)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.palantir)
}

group = "org.agrfesta.sh"
version = "0.5.10"

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
	implementation(project(":persistence"))
	implementation(project(":providers"))
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.postgresql:postgresql")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation(libs.ktor.core)
	implementation(libs.ktor.okhttp)
	implementation(libs.ktor.content.negotiation)
	implementation(libs.ktor.jackson)
	implementation(libs.flyway.core)
	implementation(libs.arrow.core)
	runtimeOnly(libs.flyway.postgres)

	testImplementation(testFixtures(project(":core")))
	testImplementation(testFixtures(project(":persistence")))
	testImplementation(testFixtures(project(":providers")))
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(module = "junit")
		exclude(module = "junit-vintage-engine")
	}
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.junit.jupiter:junit-jupiter-api")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
	testImplementation(libs.kotest.assertions.core)
	testImplementation(libs.kotest.assertions.arrow)
	testImplementation(libs.kotest.assertions.json)
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation(libs.testcontainers.redis)
	testImplementation("io.rest-assured:rest-assured")
	testImplementation(libs.mockk)
	testImplementation(libs.springmockk)
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
