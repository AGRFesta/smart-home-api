package org.agrfesta.sh.api

import org.agrfesta.sh.api.controllers.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class SmartHomeApplicationTests: AbstractIntegrationTest() {
	companion object {
		@Container
		@ServiceConnection
		val postgres = createPostgresContainer()

		@Container
		@ServiceConnection
		val redis = createRedisContainer()
	}

	@Test fun contextLoads() {/* just loads context */}
}
