package org.agrfesta.sh.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SmartHomeApplicationTests {

	companion object {

		@Container
		@ServiceConnection
		val postgres: PostgreSQLContainer<*> = DockerImageName.parse("timescale/timescaledb:latest-pg16")
			.asCompatibleSubstituteFor("postgres")
			.let { PostgreSQLContainer(it) }

	}

	@Test
	fun contextLoads() {}

}
