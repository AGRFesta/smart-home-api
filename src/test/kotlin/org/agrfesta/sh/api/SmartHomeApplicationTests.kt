package org.agrfesta.sh.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SmartHomeApplicationTests {

	companion object {

		@Container
		@ServiceConnection
		val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

	}

	@Test
	fun contextLoads() {}

}
