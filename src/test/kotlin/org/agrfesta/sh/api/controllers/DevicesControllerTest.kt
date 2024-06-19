package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest//(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DevicesControllerTest {

    companion object {

        @Container
        @ServiceConnection
        var postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    }

    @MockBean private lateinit var switchBotDevicesClient: SwitchBotDevicesClient

    @Autowired private lateinit var devicesRepository: DevicesRepository

    @Test
    fun runTest() {
        devicesRepository.findAll()
    }

}
