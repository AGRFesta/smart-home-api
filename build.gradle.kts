plugins {
	alias(libs.plugins.jvm) // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
	alias(libs.plugins.springboot)
	alias(libs.plugins.springbootManagement)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.palantir)
	alias(libs.plugins.flyway)
}

group = "org.agrfesta.sh"
version = "0.1.0"

val smartHomeDbUrl: String by project
val smartHomeDbUser: String by project
val smartHomeDbPassword: String by project

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation(libs.ktor.core)
	implementation(libs.ktor.okhttp)
	implementation("org.postgresql:postgresql:42.7.3")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(module = "junit")
		exclude(module = "junit-vintage-engine")
	}
	testImplementation("org.junit.jupiter:junit-jupiter-api")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
	testImplementation(libs.kotest.assertions.core)
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

flyway {
	url = smartHomeDbUrl
	user = smartHomeDbUser
	password = smartHomeDbPassword
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
